var page = new tabris.Page({
  title: 'Hello, World!',
  topLevel: true
});

var button = new tabris.Button({
  text: 'Login',
  layoutData: {centerX: 0, top: 100}
}).appendTo(page);

button.on('select', function() {
  
  window.plugins.googleplus.login(
		{
		},
		function (obj) {
			navigator.notification.alert(obj);
		},
		function (msg) {
			navigator.notification.alert(msg)
		}
	);
	
});

page.open();