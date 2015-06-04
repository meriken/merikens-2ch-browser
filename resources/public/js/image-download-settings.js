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
 *  along with Meriken's 2ch Browser.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  Additional permission under GNU GPL version 3 section 7
 *
 *  If you modify this Program, or any covered work, by linking or
 *  combining it with Clojure (or a modified version of that
 *  library), containing parts covered by the terms of EPL, the licensors
 *  of this Program grant you additional permission to convey the
 *  resulting work.{Corresponding Source for a non-source form of such
 *  a combination shall include the source code for the parts of clojure
 *  used as well as that of the covered work.}
 *
 */



function popupWindowResizeComponents() {
	var hDiff =  $('#popup-window-title').outerWidth()
	            + parseInt($('body').css('padding-left').replace("px", ""))
	            + parseInt($('body').css('padding-right').replace("px", ""))
				- $(window).innerWidth();
	
	$('#popup-window-title').width($('#popup-window-title').width() - hDiff);
	$('#save-downloaded-images-checkbox-label').width($('#save-downloaded-images-checkbox-label').width() - hDiff);
	$('#use-image-proxy-checkbox-label').width($('#use-image-proxy-checkbox-label').width() - hDiff);
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
						+ $('#save-downloaded-images-checkbox-label').outerHeight(true)
						+ $('#use-image-proxy-checkbox-label').outerHeight(true)
						+ $('#popup-window-submit-button').outerHeight(true)
						+ parseInt($('body').css('padding-bottom').replace("px", ""));
			var vDiff = height - $(window).innerHeight();
						
			window.resizeTo(
				width + (window.outerWidth - document.documentElement.clientWidth),
				height + (window.outerHeight - document.documentElement.clientHeight)
			);
		
			window.moveBy((-hDiff / 2), -(vDiff / 2))
			
			setTimeout(function () {
				opener.setImageDownloadSettingsWindowSize(window.innerWidth, window.innerHeight);
			}, 500);

			$(window).resize(function() {
				popupWindowResizeComponents();
			});
		}, 500);
	} 
); 
