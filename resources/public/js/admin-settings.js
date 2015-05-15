function adminSettingsWindowResizeComponents() {
	var hDiff =  $('#popup-window-title').outerWidth()
	            + parseInt($('body').css('padding-left').replace("px", ""))
	            + parseInt($('body').css('padding-right').replace("px", ""))
				- $(window).innerWidth();
	
	$('#popup-window-title').width($('#popup-window-title').width() - hDiff);
	$('#allow-new-user-accounts-checkbox-label').width($('#allow-new-user-accounts-checkbox-label').width() - hDiff);
	$('#popup-window-submit-button').width($('#popup-window-submit-button').width() - hDiff);
}

$(document).ready(function() 
    { 
		setTimeout(function () {
			var width   = $('#popup-window-title').outerWidth()
						+ parseInt($('body').css('padding-left').replace("px", ""))
						+ parseInt($('body').css('padding-right').replace("px", ""));
			var hDiff = width - $(window).innerWidth();
			
			var height =  $('#popup-window-title').outerHeight(true)
						+ $('#allow-new-user-accounts-checkbox-label').outerHeight(true)
						+ $('#popup-window-submit-button').outerHeight(true)
						+ parseInt($('body').css('padding-bottom').replace("px", ""));
			var vDiff = height - $(window).innerHeight();
						
			window.resizeTo(
				width + (window.outerWidth - document.documentElement.clientWidth),
				height + (window.outerHeight - document.documentElement.clientHeight)
			);
		
			window.moveBy((-hDiff / 2), -(vDiff / 2))
			
			setTimeout(function () {
				opener.setAdminSettingsWindowSize(window.innerWidth, window.innerHeight);
			}, 500);

			$(window).resize(function() {
				adminSettingsWindowResizeComponents();
			});
		}, 500);
	} 
); 
