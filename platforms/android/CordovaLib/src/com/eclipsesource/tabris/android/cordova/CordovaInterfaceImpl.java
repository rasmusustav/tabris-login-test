package com.eclipsesource.tabris.android.cordova;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Pair;
import android.util.SparseArray;

import com.eclipsesource.tabris.android.TabrisActivity;
import com.eclipsesource.tabris.android.internal.toolkit.IActivityResultListener;
import com.eclipsesource.tabris.android.internal.toolkit.IAndroidWidgetToolkit;
import com.eclipsesource.tabris.android.internal.toolkit.IRequestPermissionResultListener;
import com.eclipsesource.tabris.android.internal.toolkit.IStartActivityForResultListener;

import org.apache.cordova.CallbackMap;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONException;

import java.util.concurrent.ExecutorService;

public class CordovaInterfaceImpl implements CordovaInterface {

  private final TabrisActivity activity;
  private final ExecutorService executorService;
  private final CallbackMap permissionResultCallbacks;
  private final SparseArray<Object> requestCodes;
  private CordovaPlugin activityResultCallback;
  private int activityResultRequestCode;

  public CordovaInterfaceImpl( final TabrisActivity activity, ExecutorService executorService ) {
    this.activity = activity;
    this.executorService = executorService;
    permissionResultCallbacks = new CallbackMap();
    requestCodes = new SparseArray<>();
    final IAndroidWidgetToolkit widgetToolkit = activity.getWidgetToolkit();
    widgetToolkit.addRequestPermissionResult( new IRequestPermissionResultListener() {
      @Override
      public void permissionsResultReceived( int requestCode, String[] permissions, int[] grantResults ) {
        try {
          onRequestPermissionResult( requestCode, permissions, grantResults );
        } catch( Exception e ) {
          widgetToolkit.showError( e );
        }
      }
    } );
    widgetToolkit.addStartActivityForResultListener( new IStartActivityForResultListener() {
      @Override
      public void startActivityForResult( Intent intent, int requestCode, Bundle bundle ) {
        activityResultRequestCode = requestCode;
      }
    } );
    widgetToolkit.addActivityResultListener( new IActivityResultListener() {
      @Override
      public void receivedActivityResult( int requestCode, int resultCode, Intent intent ) {
        if( activityResultCallback != null ) {
          try {
            Object originalRequestCode = requestCodes.get( requestCode );
            if( originalRequestCode instanceof Integer ) {
              activity.getWidgetToolkit().getRequestCodePool().returnRequestCode( requestCode );
              activityResultCallback.onActivityResult( ( int )originalRequestCode, resultCode, intent );
            } else {
              // activity has be start directly via Activity.startForResult();
              activityResultCallback.onActivityResult( requestCode, resultCode, intent );
            }
          } catch( Exception e ) {
            widgetToolkit.showError( e );
          }
        }
        activityResultCallback = null;
      }
    } );
  }

  @Override
  public void startActivityForResult( CordovaPlugin cordovaPlugin, Intent intent, int requestCode ) {
    setActivityResultCallback( cordovaPlugin );
    try {
      int sanitizedRequestCode = activity.getWidgetToolkit().getRequestCodePool().takeRequestCode();
      requestCodes.put( sanitizedRequestCode, requestCode );
      activity.startActivityForResult( intent, sanitizedRequestCode );
    } catch( Exception e ) {
      activityResultCallback = null;
      throw e;
    }
  }

  @Override
  public void setActivityResultCallback( CordovaPlugin plugin ) {
    if( activityResultCallback != null ) {
      activityResultCallback.onActivityResult( activityResultRequestCode, Activity.RESULT_CANCELED, null );
    }
    activityResultCallback = plugin;
  }

  @Override
  public Activity getActivity() {
    return activity;
  }

  @Override
  public Object onMessage( String id, Object data ) {
    if( id != null && id.equals( "exit" ) ) {
      activity.finish();
    }
    return null;
  }

  @Override
  public ExecutorService getThreadPool() {
    return executorService;
  }

  public void onRequestPermissionResult( int requestCode, String[] permissions,
                                         int[] grantResults ) throws JSONException {
    Pair<CordovaPlugin, Integer> callback = permissionResultCallbacks.getAndRemoveCallback( requestCode );
    if( callback != null ) {
      callback.first.onRequestPermissionResult( callback.second, permissions, grantResults );
    }
  }

  public void requestPermission( CordovaPlugin plugin, int requestCode, String permission ) {
    String[] permissions = new String[ 1 ];
    permissions[ 0 ] = permission;
    requestPermissions( plugin, requestCode, permissions );
  }

  public void requestPermissions( CordovaPlugin plugin, int requestCode, String[] permissions ) {
    if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
      int mappedRequestCode = permissionResultCallbacks.registerCallback( plugin, requestCode );
      getActivity().requestPermissions( permissions, mappedRequestCode );
    }
  }

  @Override
  public boolean hasPermission( String permission ) {
    if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
      return activity.checkSelfPermission( permission ) == PackageManager.PERMISSION_GRANTED;
    }
    return true;
  }

}
