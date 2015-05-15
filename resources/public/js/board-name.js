function boardNameWindowResizeComponents() {
	var hDiff =  $('#popup-window-title').outerWidth()
	            + parseInt($('body').css('padding-left').replace("px", ""))
	            + parseInt($('body').css('padding-right').replace("px", ""))
				- $(window).innerWidth();
	
	$('#popup-window-title'        ).width($('#popup-window-title'        ).width() - hDiff);
	$('#board-name'                ).width($('#board-name'                ).width() - hDiff);
	$('#popup-window-submit-button').width($('#popup-window-submit-button').width() - hDiff);
}

$(document).ready(function() 
    { 
		setTimeout(function () {
			var width =  $('#popup-window-title').outerWidth(false)
						+ parseInt($('body').css('padding-left').replace("px", ""))
						+ parseInt($('body').css('padding-right').replace("px", ""));
			var hDiff = width - $(window).innerWidth();
			
			var height = $('#popup-window-title'         ).outerHeight(true)
						+ $('#board-name'                ).outerHeight(true)
						+ $('#popup-window-submit-button').outerHeight(true)
						+ parseInt($('body').css('padding-bottom').replace("px", ""));		
			var vDiff = height - $(window).innerHeight();
			
			window.resizeTo(
				width + (window.outerWidth - document.documentElement.clientWidth),
				height + (window.outerHeight - document.documentElement.clientHeight)
			);
		
			window.moveBy((-hDiff / 2), -(vDiff / 2))

			setTimeout(function () {
				opener.setBoardNameWindowSize(window.innerWidth, window.innerHeight);
			}, 500);
			
			$(window).resize(function() {
				boardNameWindowResizeComponents();
			});
		}, 500);
	} 
); 

