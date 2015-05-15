/*
 *  This file is part of Meriken's 2ch Browser.
 * 
 *  Meriken's 2ch Browser is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Meriken's 2ch Browser is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 */



function userSettingsWindowResizeComponents() {
	var hDiff =  $('#popup-window-title').outerWidth()
	            + parseInt($('body').css('padding-left').replace("px", ""))
	            + parseInt($('body').css('padding-right').replace("px", ""))
				- $(window).innerWidth();
	
	$('#popup-window-title'           ).width($('#popup-window-title'           ).width() - hDiff);
	$('#use-p2-to-post-checkbox-label').width($('#use-p2-to-post-checkbox-label').width() - hDiff);
	$('#p2-email'                     ).width($('#p2-email'                     ).width() - hDiff);
	$('#p2-password'                  ).width($('#p2-password'                  ).width() - hDiff);
	$('#use-ronin-checkbox-label'     ).width($('#use-ronin-checkbox-label'     ).width() - hDiff);
	$('#ronin-email'                  ).width($('#ronin-email'                  ).width() - hDiff);
	$('#ronin-secret-key'             ).width($('#ronin-secret-key'             ).width() - hDiff);
	$('#popup-window-submit-button'   ).width($('#popup-window-submit-button').width() - hDiff);
}

$(document).ready(function() 
    { 
		setTimeout(function () {
			var width =  $('#popup-window-title').outerWidth(false)
						+ parseInt($('body').css('padding-left').replace("px", ""))
						+ parseInt($('body').css('padding-right').replace("px", ""));
			var hDiff = width - $(window).innerWidth();
			
			var height = $('#popup-window-title'           ).outerHeight(true)
						+ $('#use-p2-to-post-checkbox-label').outerHeight(true)
						+ $('#p2-email'                     ).outerHeight(true)
						+ $('#p2-password'                  ).outerHeight(true)
						+ $('#use-ronin-checkbox-label'     ).outerHeight(true)
						+ $('#ronin-email'                  ).outerHeight(true)
						+ $('#ronin-secret-key'             ).outerHeight(true)
						+ $('#popup-window-submit-button'   ).outerHeight(true)
						+ parseInt($('body').css('padding-bottom').replace("px", ""));		
			var vDiff = height - $(window).innerHeight();
			
			// alert(width + ', ' + height + ', ' + hDiff + ', ' + vDiff);
			
			window.resizeTo(
				width + (window.outerWidth - document.documentElement.clientWidth),
				height + (window.outerHeight - document.documentElement.clientHeight)
			);
		
			window.moveBy((-hDiff / 2), -(vDiff / 2))

			setTimeout(function () {
				opener.setUserSettingsWindowSize(window.innerWidth, window.innerHeight);
			}, 500);
			
			$(window).resize(function() {
				userSettingsWindowResizeComponents();
			});
		}, 500);
	} 
); 

