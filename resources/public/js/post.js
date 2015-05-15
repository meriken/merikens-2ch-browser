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



/////////////////
// POST WINDOW //
/////////////////

var threadURL = null;
var autosaveInterval = 10000;

function postWindowResizeComponents() {
	var hDiff =  $('#popup-window-title').width()
	            + parseInt($('body').css('padding-left').replace("px", ""))
	            + parseInt($('body').css('padding-right').replace("px", ""))
				+ parseInt($("#popup-window-title").css("border-left-width").replace("px", ""))
				+ parseInt($("#popup-window-title").css("border-right-width").replace("px", ""))
				+ parseInt($("#popup-window-title").css("padding-left").replace("px", ""))
				+ parseInt($("#popup-window-title").css("padding-right").replace("px", ""))
				- $(window).innerWidth();
	
	$('#popup-window-title').width($('#popup-window-title').width() - hDiff);
	$('#post-page-handle').width($('#post-page-handle').width() - hDiff);
	$('#post-page-email').width($('#post-page-email').width() - hDiff);
	$('#post-page-message').width($('#post-page-message').width() - hDiff);
	$('#popup-window-submit-button').width($('#popup-window-submit-button').width() - hDiff);
	try {
		$('#post-page-thread-title').width($('#post-page-thread-title').width() - hDiff);
	} catch (e) {
	}
	
	var vDiff =   parseInt($("#popup-window-title").css("margin-top").replace("px", ""))
				+ parseInt($("#popup-window-title").css("border-top-width").replace("px", ""))
				+ parseInt($("#popup-window-title").css("padding-top").replace("px", ""))
				+ $('#popup-window-title').height()
				+ parseInt($("#popup-window-title").css("padding-bottom").replace("px", ""))
				+ parseInt($("#popup-window-title").css("border-bottom-width").replace("px", ""))
				
				+ parseInt($("#post-page-handle").css("border-top-width").replace("px", ""))
				+ parseInt($("#post-page-handle").css("padding-top").replace("px", ""))
				+ $('#post-page-handle').height()
				+ parseInt($("#post-page-handle").css("padding-bottom").replace("px", ""))
				+ parseInt($("#post-page-handle").css("border-bottom-width").replace("px", ""))
				
				+ parseInt($("#post-page-email").css("border-top-width").replace("px", ""))
				+ parseInt($("#post-page-email").css("padding-top").replace("px", ""))
				+ $('#post-page-email').height()
				+ parseInt($("#post-page-email").css("padding-bottom").replace("px", ""))
				+ parseInt($("#post-page-email").css("border-bottom-width").replace("px", ""))
				
				+ parseInt($("#post-page-message").css("border-top-width").replace("px", ""))
				+ parseInt($("#post-page-message").css("padding-top").replace("px", ""))
				+ $('#post-page-message').height()
				+ parseInt($("#post-page-message").css("padding-bottom").replace("px", ""))
				+ parseInt($("#post-page-message").css("border-bottom-width").replace("px", ""))
				
				+ parseInt($("#popup-window-submit-button").css("border-top-width").replace("px", ""))
				+ parseInt($("#popup-window-submit-button").css("padding-top").replace("px", ""))
				+ $('#popup-window-submit-button').height()
				+ parseInt($("#popup-window-submit-button").css("padding-bottom").replace("px", ""))
				+ parseInt($("#popup-window-submit-button").css("border-bottom-width").replace("px", ""))
				
				+ parseInt($('body').css('padding-bottom').replace("px", ""))
				
				- $(window).innerHeight();		
	
	$('#post-page-message').height($('#post-page-message').height() + $(window).innerHeight() - $('html').height());	
}

$(document).ready(function() 
    { 
		$(window).trigger('resize');
		if ($(window).height() < $('html').height())
			window.resizeBy(0, $('html').height() - $(window).innerHeight());
	} 
); 

$(window).resize(function() {
	postWindowResizeComponents();
});

function autosaveDraft() {
	$.ajax({ 
		url: '/api-autosave-draft'
		     + '?thread-url=' + encodeURIComponent(threadURL)
		     + '&handle='     + encodeURIComponent($("#post-page-handle").val())
		     + '&email='      + encodeURIComponent($("#post-page-email").val())
		     + '&draft='      + encodeURIComponent($("#post-page-message").val()),
		async: true,
		success: function(result){ 
		},
		error:  function(result, textStatus, errorThrown){
		}
	});
	setTimeout(autosaveDraft, autosaveInterval);
}

$(document).ready(function() 
    { 
		setTimeout(autosaveDraft, autosaveInterval);
	} 
); 

