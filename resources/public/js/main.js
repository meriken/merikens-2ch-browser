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



/********/
/* AJAX */
/********/

$.ajaxSetup({
  cache: false
});



/*****************/
/* ZeroClipboard */
/*****************/

ZeroClipboard.config( { swfPath: "/js/ZeroClipboard.swf" } );

function isZeroClipboardAvailable() {
	var state = ZeroClipboard.state();
	
	return !(state.flash.deactivated || state.flash.disabled || state.flash.outdated || state.flash.overdue);
}

function createZeroClipboardObjectIfNecessary() {
	if (!isZeroClipboardAvailable()) {
		console.log(ZeroClipboard.state());
		ZeroClipboard.destroy();
		ZeroClipboard.create();
	}
}



/*********************/
/* UTILITY FUNCTIONS */
/*********************/

var currentMouseX = 0;
var currentMouseY = 0;
var doesWindowHaveFocus = true;

$(document).ready(function($) {
    $(document).mousemove(function(event) {
        currentMouseX = event.pageX;
        currentMouseY = event.pageY;

		// ZeroClipboard prevents mouseleave events from firing.
		if ($('#popup-menus').html()) {
			var onMenu = false;
			$('.popup-menu').each(function(index, element) {
 				if (isMouseCursorOnElement(element)) {
					onMenu = true;
					return false;
				}
            });
			// ZeroClipboard messes up event.pageX and event.pageY, too.
			$('.popup-menu-item').each(function(index, element) {
 				if ($(element).hasClass('zeroclipboard-is-hover')) {
					onMenu = true;
					return false;
				}
            });
			if (!onMenu)
				removeAllPopupMenus();
		}
    });
	
	$(window).blur(function(event){
		doesWindowHaveFocus = false;
		removeAllPopupMenus();
		removeAllFloatingPosts()
	});
	
	$(window).focus(function(event){
		doesWindowHaveFocus = true;
		currentMouseX = event.pageX;
		currentMouseY = event.pageY;
	});
});

function isMouseCursorOnElement(element) {
	return    Math.floor($(element).offset().left) <= currentMouseX && currentMouseX < $(element).offset().left + $(element).outerWidth(false)
		    && Math.floor($(element).offset().top)  <= currentMouseY && currentMouseY < $(element).offset().top  + $(element).outerHeight(false);
}

function escapeHTML(s) { 
	if (s == null) {
		return null;
	} else {
    	return s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
	}
}

function randomElementID()
{
    var text = "";
    var possible = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    for( var i=0; i < 5; i++ )
        text += possible.charAt(Math.floor(Math.random() * possible.length));

    return text;
}

function MSIEVersion() {
	var ua = window.navigator.userAgent;
	var msie = ua.indexOf("MSIE ");

	if (msie > -1) {
		// Up to IE 10
		return parseInt(ua.substring(msie + 5, ua.indexOf(".", msie)));
	} else if (!!navigator.userAgent.match(/Trident.*rv\:[0-9]+\./)) {
		// IE 11+
		var rv = ua.indexOf("rv:")
		return parseInt(ua.substring(rv + 3, ua.indexOf(".", rv)));
	} else {
		// If another browser, return 0
		return 0;
	}
}

function isBrowserFirefox() {
	return navigator.userAgent.toLowerCase().indexOf('firefox') > -1;
}

function openURLInNewWindowWithReferrerDisabled(url) {
	if (MSIEVersion() > 0) {
		// IE doesn't like data URLs.
		open(  "http://page2.skr.jp/gate.php?u="
			 + encodeURIComponent(url)
			 + "&d=-1"); // What's this?
	} else {
		// Works in Chrome 36.0.
		// Does not work in Firefox 30.0.
		// open(  "data:text/html;charset=utf-8, <html><meta http-equiv=\"refresh\" content=\"0;URL=&#39;"
		//	 + url
		//	 + "&#39;\"></html>");

		// Works both in Chrome 36.0 and Firefox 30.0.
		open(  "data:text/html;charset=utf-8, "
		     + encodeURIComponent(  "<html><meta http-equiv=\"refresh\" content=\"0;URL='"
		                          + url
		                          + "'\"></html>"));
	}
}

var ajaxSpinnerBars = 
		  "<div class='ajax-spinner-bars'>"
		+ "<div class='bar-1'></div>"
		+ "<div class='bar-2'></div>"
		+ "<div class='bar-3'></div>"
		+ "<div class='bar-4'></div>"
		+ "<div class='bar-5'></div>"
		+ "<div class='bar-6'></div>"
		+ "<div class='bar-7'></div>"
		+ "<div class='bar-8'></div>"
		+ "<div class='bar-9'></div>"
		+ "<div class='bar-10'></div>"
		+ "<div class='bar-11'></div>"
		+ "<div class='bar-12'></div>"
		+ "<div class='bar-13'></div>"
		+ "<div class='bar-14'></div>"
		+ "<div class='bar-15'></div>"
		+ "<div class='bar-16'></div>"
		+ "</div>";

var ajaxSpinnerBarsRed = 
		  "<div class='ajax-spinner-bars red'>"
		+ "<div class='bar-1'></div>"
		+ "<div class='bar-2'></div>"
		+ "<div class='bar-3'></div>"
		+ "<div class='bar-4'></div>"
		+ "<div class='bar-5'></div>"
		+ "<div class='bar-6'></div>"
		+ "<div class='bar-7'></div>"
		+ "<div class='bar-8'></div>"
		+ "<div class='bar-9'></div>"
		+ "<div class='bar-10'></div>"
		+ "<div class='bar-11'></div>"
		+ "<div class='bar-12'></div>"
		+ "<div class='bar-13'></div>"
		+ "<div class='bar-14'></div>"
		+ "<div class='bar-15'></div>"
		+ "<div class='bar-16'></div>"
		+ "</div>";

var ajaxSpinnerBarsWhite = 
		  "<div class='ajax-spinner-bars white'>"
		+ "<div class='bar-1'></div>"
		+ "<div class='bar-2'></div>"
		+ "<div class='bar-3'></div>"
		+ "<div class='bar-4'></div>"
		+ "<div class='bar-5'></div>"
		+ "<div class='bar-6'></div>"
		+ "<div class='bar-7'></div>"
		+ "<div class='bar-8'></div>"
		+ "<div class='bar-9'></div>"
		+ "<div class='bar-10'></div>"
		+ "<div class='bar-11'></div>"
		+ "<div class='bar-12'></div>"
		+ "<div class='bar-13'></div>"
		+ "<div class='bar-14'></div>"
		+ "<div class='bar-15'></div>"
		+ "<div class='bar-16'></div>"
		+ "</div>";

function centerPopupWindow(width, height)
{
    var left = (window.screenLeft ? window.screenLeft : window.screenX) + (window.innerWidth  - width)  / 2;
    var top  = (window.screenTop  ? window.screenTop  : window.screenY) + (window.innerHeight - height) / 2;
	
	return "width = " + width + ", height = " + height + ", left = " + left + ", top = " + top;
}

function removeOptionsFromThreadURL(threadURL) {
	return threadURL.replace(/[0-9ln,+-]+ *$/, "");
}

function keepElementWithinWindow(selector, activateZeroClipboard) {
	if (typeof activateZeroClipboard == 'undefined') activateZeroClipboard = null;

	setTimeout(function () {
		if ($(selector).outerWidth(false) > $(window).width()) {
			$(selector).css("overflow-x", "scroll");
			$(selector).outerWidth($(window).width());
		}
		if ($(selector).outerHeight(false) > $(window).height()) {
			$(selector).css("overflow-y", "scroll");
			$(selector).outerHeight($(window).height());
		}
		setTimeout(function () {
			try {
				if ($(selector).offset().left + $(selector).outerWidth(false) > $(window).width())
					$(selector).css("left", $(selector).offset().left - ($(selector).offset().left + $(selector).outerWidth(false) - $(window).width()));
				if ($(selector).offset().top + $(selector).outerHeight(false) > $(window).height())
					$(selector).css("top", $(selector).offset().top - ($(selector).offset().top + $(selector).outerHeight() - $(window).height()));
			} catch (e) {
			}
			$(selector).css({'opacity': 0.95});
			if (activateZeroClipboard && isZeroClipboardAvailable())
				setTimeout(activateZeroClipboard, 0);
		}, 0);
	}, 0);
}

function createPopupMenu(event, id) {
	return $("<div />")
	            .attr("id", id)
	            .attr("class", "popup-menu")
	            .attr('oncontextmenu', 'return false;')
	            // .attr("onmouseleave", "$(event.target).remove(); removeFloatingPosts(event);")
	            .mouseleave(function(event) { $(event.target).remove(); removeFloatingPosts(event); })
	            .css("top", event.clientY - 12)
	            .css("left", event.clientX - 32)
	            .css({'opacity': 0});
}

function createElementIdForPost(service, board, threadNo, postIndex, elementType) {
	return ('post-' + elementType + '-' + service + '-' + board + '-' + threadNo + '-' + postIndex).replace(/[.\/]/g, '_');
}

function createSelectorForPost(service, board, threadNo, postIndex, elementType) {
	var sel = '#' + createElementIdForPost(service, board, threadNo, postIndex, elementType);
	// console.log('createSelectorForPost(): ' + sel);
	return sel;
}

if (!Date.now) {
    Date.now = function() { return new Date().getTime(); }
}



/***************/
/* MAIN WINDOW */
/***************/

var displayLeftPanes = true;
var displayThreadList = true;
var threadContentHeightRatio = 1.6;


function updateVerticalDraggableArea() {
	var left = 0;
	
	if (displayLeftPanes) {
		left = $('div#left-panes-wrapper').outerWidth(false);
		$('#vertical-draggable-area').draggable( 'enable' );
	} else {
		$('#vertical-draggable-area').draggable( 'disable' );
	}
	
	$('#vertical-draggable-area').offset({left: left, top: $('#vertical-draggable-area').offset().top});
}

function toggleLeftPanes() {
	if (displayLeftPanes) {
		$("#left-panes-wrapper").hide();
	} else {
		$("#left-panes-wrapper").show();
	}
	displayLeftPanes= !displayLeftPanes;
	setTimeout(function() { updateVerticalDraggableArea(); adjustComponents(); }, 0);
}

function updateRightPanesDraggableArea() {
	var top = 0;
	
	if (displayThreadList) {
		top = $( "#thread-list" ).offset().top + $( "#thread-list" ).height()
											    + parseInt($('#thread-list').css('border-top-width').replace('px', ""))
											    + parseInt($('#thread-list').css('border-bottom-width').replace('px', ""));
		$('#right-panes-draggable-area').draggable( 'enable' );
	} else {
		$('#right-panes-draggable-area').draggable( 'disable' );
	}
	
	$('#right-panes-draggable-area').width($('#thread-titles').width() - 1); // for IE
	$('#right-panes-draggable-area').offset({top: top, 
											  left: $('#right-panes-draggable-area').offset().left});
}

function toggleThreadList() {
	console.log('toggleThreadList()');
	if (displayThreadList) {
		$("#board-names").hide();
		$("#thread-list-tools-below-board-name").hide();
		$("#thread-list").hide();
	} else {
		$("#board-names").show();	
		$("#thread-list-tools-below-board-name").show();
		$("#thread-list").show();	
	}
	displayThreadList = !displayThreadList;
	setTimeout(adjustComponents, 0);
}

function availableSpaceHeight() {
	// alert($("#right-panes").height());
	
	var h =                       $("#right-panes").height()
	
								// - $("#thread-list-toggle-button").outerHeight(true)
								- parseInt($('#thread-list').css('margin-bottom').replace('px', ""))
																
								- $("#thread-titles").outerHeight(true)
								- $("#thread-content-tools-below-thread-title").outerHeight(true)
								- ($("#thread-content").outerHeight(true) - $("#thread-content").height())
								- $("#thread-content-tools-at-bottom").outerHeight(true);

	if (displayThreadList) {
		h -=   $("#board-names").outerHeight(true)
			 + $("#thread-list-tools-below-board-name").outerHeight(true)
			 + ($("#thread-list").outerHeight(false) - $("#thread-list").height())
	}
	
	return h;
}

function setThreadListHeight(height) {
	$('#thread-list').height(height);
	try {
		$('#thread-list-fixed-table-container').height($('#thread-list').height() - $('#thread-list-fixed-table-container').css('padding-top').replace("px", ""));
	} catch (e) {
	}

	$('#thread-content').height(availableSpaceHeight() - $('#thread-list').height() );
	
	threadContentHeightRatio = $('#thread-content').height() / $('#thread-list').height();	
}

$(function() {
	$('#right-panes-draggable-area').draggable({
		drag: function( event, ui ) {
			setTimeout(function() {
				setThreadListHeight(  $( ui.helper ).offset().top 
			                    - $( "#thread-list" ).offset().top 
								- parseInt($('#thread-list').css('border-top-width').replace('px', ""))
								- parseInt($('#thread-list').css('border-bottom-width').replace('px', ""))); 
				$('#fixed-thread-heading').css('top', $('#thread-content').offset().top + parseInt($('#thread-content').css('border-top-width').replace('px', "")));
			}, 0);
		},
		start: function( event, ui ) {
			$('#thread-list').css('max-height', ($('#thread-list').height() + $('#thread-content').height() - $('#thread-content').css('min-height').replace('px', "")) + 'px');
		},
		stop: function( event, ui ) {
			updateRightPanesDraggableArea();
			$('#fixed-thread-heading').css('top', $('#thread-content').offset().top + parseInt($('#thread-content').css('border-top-width').replace('px', "")));
		},
		containment: "document",
		axis: "y"
	});
});

$(function() {
	$('#vertical-draggable-area').draggable({
		drag: function( event, ui ) {
			setTimeout(function() { 
				$('div#left-panes-wrapper').width($('#vertical-draggable-area').offset().left);
				$('div#right-panes').offset({left: $('#vertical-draggable-area').offset().left,
											  top: $('div#right-panes').offset().top});
				adjustComponents();
			});
		},
		start: function( event, ui ) {
			// $('#left-pane-wrapper').css('max-width', ($('#right-panes').width() + $('#left-pane-wrapper').width() - parseInt($('#right-panes').css('min-width').replace('px', ""))) + 'px');
		},
		stop: function( event, ui ) {
			updateVerticalDraggableArea();
		},
		containment: "#panes",
		axis: "x"
	});
});
                             
function setThreadListFixedTableContainerHeight() {
	if ($('#thread-list-fixed-table-container').length > 0) 
		$('#thread-list-fixed-table-container').height($('#thread-list').height() - $('#thread-list-fixed-table-container').css('padding-top').replace("px", ""));
}

function adjustComponents() {
	if (displayLeftPanes) {
		$("#right-panes").width(  $(window).innerWidth()
								- $("#left-panes-wrapper").width() 
								- parseInt($('#right-panes').css('padding-left').replace('px', "")) 
								- parseInt($('#right-panes').css('padding-right').replace('px', "")));
	} else {
		$("#right-panes").width(  $(window).innerWidth()
								- parseInt($('#right-panes').css('padding-left').replace('px', "")) 
								- parseInt($('#right-panes').css('padding-right').replace('px', "")));
	}
	
	updateBoardNames();
	updateThreadTitles();

	var panesHeight   =   $(window).innerHeight()
	                    /* - $("#page-top").height() 
	                    - $("#page-top").css("border-bottom-width").replace("px", "") */;
	
    $("#panes").height(panesHeight);
    $("#left-panes-wrapper").height(panesHeight);
    $("#left-panes").height(panesHeight);
    $("#right-panes").height(panesHeight);

	if (displayLeftPanes) {
		$('#vertical-draggable-area').css("left", $("#left-panes-wrapper").outerWidth(false));
		$('#right-panes').css("left", $("#left-panes-wrapper").outerWidth(false));
	} else {
		$('#right-panes').css("left", 0);
	}
	
	// For IE
    $('#right-panes-draggable-area').width($('#thread-list').width()
	                                        + parseInt($("#thread-list").css("border-left-width").replace("px", ""))
											+ parseInt($("#thread-list").css("border-right-width").replace("px", "")));

	$('#vertical-draggable-area').draggable("option",
											 "containment",
											 [parseInt($('#left-panes-wrapper').css('min-width').replace('px', "")) + $("#left-panes-toggle-button").outerWidth(false),
											  0,
											    $(document).width()
											  - parseInt($('#right-panes').css('min-width').replace('px', ""))
											  - parseInt($('#right-panes').css('padding-left').replace('px', ""))
											  - parseInt($('#right-panes').css('padding-right').replace('px', "")),
											  $(document).height()]);
	
	$('#thread-search-text-field').width($('#thread-list-tools-below-board-name').width() * 0.22);
	var boardURLTextFieldWidth =   $('#thread-list-tools-below-board-name').width()
	                             - $('#refresh-thread-list-button').outerWidth(true)
	                             - $('#automatic-updates-for-thread-list-checkbox-label').outerWidth(true)
	                             - $('#open-post-window-button').outerWidth(true)
	                             - $('#log-list-mode-checkbox-label').outerWidth(true)
	                             - $('#open-original-board-button').outerWidth(true)
								 - ($('#board-url-text-field').outerWidth(true) - $('#board-url-text-field').width())
	                             - $('#star-wrapper-for-favorite-board').outerWidth(true)
	                             - $('#thread-search-text-field').outerWidth(true);
	$('#board-url-text-field').width(boardURLTextFieldWidth);

	$('#post-search-text-field').width($('#thread-content-tools-below-thread-title').width() * 0.22);
	var threadURLTextFieldWidth = $('#thread-content-tools-below-thread-title').width()
	                             // - $('#go-forward-button').outerWidth(true)
	                             // - $('#go-backward-button').outerWidth(true)
								 - ($('#thread-url-text-field').outerWidth(true) - $('#thread-url-text-field').width())
	                             - $('#star-wrapper-for-favorite-thread').outerWidth(true)
	                             - $('#post-search-text-field').outerWidth(true);
	$('#thread-url-text-field').width(threadURLTextFieldWidth);
	
	// For Mac
	$('#board-url-text-field').outerHeight($('#go-forward-button').outerHeight());
	$('#thread-search-text-field').outerHeight($('#go-forward-button').outerHeight());
	$('#thread-url-text-field').outerHeight($('#go-forward-button').outerHeight());
	$('#post-search-text-field').outerHeight($('#go-forward-button').outerHeight());
	
	if ($('#left-panes').outerWidth(true) + $('#right-panes').outerWidth(true) <= $(window).width()) {
		$('#vertical-draggable-area').draggable("enable");
	} else {
		$('#vertical-draggable-area').draggable("disable");
	}
	
	setImageViewerSize();
		
	setTimeout(function () {
		if (displayThreadList) {
			var threadListHeight = availableSpaceHeight() / (1 + threadContentHeightRatio);
			$('#thread-list').height(threadListHeight);
			setThreadListFixedTableContainerHeight();
			$('#thread-content').height(availableSpaceHeight() * threadContentHeightRatio / (1 + threadContentHeightRatio));
		} else {
			$('#thread-content').height(availableSpaceHeight());
		}
		updateRightPanesDraggableArea();
		
		// console.log("$('div#thread-content-wrapper > .thread-heading').length:" + $('div#thread-content-wrapper > .thread-heading').length);
		if ($('div#thread-content-wrapper > div.thread-heading').length <= 0) {
			$('#fixed-thread-heading').hide();
		} else {
			updateFixedThreadHeading();
			$('#fixed-thread-heading').css('left', $('#thread-content').offset().left + parseInt($('#thread-content').css('border-left-width').replace('px', "")));
			$('#fixed-thread-heading').css('top', $('#thread-content').offset().top + parseInt($('#thread-content').css('border-top-width').replace('px', "")));
			$('#fixed-thread-heading').width($('div#thread-content-wrapper > div.thread-heading').width());
			$('#fixed-thread-heading').show();
		}
	}, 0);
}

$(window).resize(function() {
	adjustComponents();
});

$(document).ready(function() { 
	adjustComponents();
}); 

var previousLoadFavoriteBoardListRequest = null;
var loadFavoriteBoardListRequestCount = 0;

function loadFavoriteBoardList(refresh) {
	if (isFavoriteBoardBeingDragged)
		return;
	
	if (previousLoadFavoriteBoardListRequest
	    && loadFavoriteBoardListRequestCount >= 2) {
		// console.log('previousLoadFavoriteBoardListRequest.abort();');
		previousLoadFavoriteBoardListRequest.abort();
		previousLoadFavoriteBoardListRequest = null;
	}
	++loadFavoriteBoardListRequestCount;
	
	previousLoadFavoriteBoardListRequest = $.ajax({ 
		url: '/api-get-favorite-board-list'
		     + (refresh                                                                           ? '?bubbles=1&refresh=1' :
		        $('#automatic-updates-for-favorite-board-list-checkbox').is(':checked') ? '?question-mark=1' :
		                                                                                             '?question-mark=0'  ),
		async: true,
		success: function(result){
			if (!isFavoriteBoardBeingDragged)
				$('#favorite-board-list').html(result);
			previousLoadFavoriteBoardListRequest = null;
		},
		error: function(result, textStatus, errorThrown){
			previousLoadFavoriteBoardListRequest = null;
		}
	});
}

function updateFavoriteBoardList() {
	if (previousLoadFavoriteBoardListRequest) {
		previousLoadFavoriteBoardListRequest.abort();
		previousLoadFavoriteBoardListRequest = null;
	}
    loadFavoriteBoardListRequestCount = 0;
    loadFavoriteBoardList(false);
    loadFavoriteBoardList(true);
}



function openAccountMenu()
{
	$('#account-menu').show(); 
	$('#account-menu-title').unbind('click').click(function () {
		closeAccountMenu();
	});	
}

function closeAccountMenu()
{
	$('#account-menu').hide(); 
	$('#account-menu-title').unbind('click').click(function () {
		openAccountMenu();
    });		
}


function openServerInfoPanel()
{
	$('#server-info-panel').show(); 
	$('#server-info-panel-title').unbind('click').click(function () {
		closeServerInfoPanel();
	});	
}

function closeServerInfoPanel()
{
	$('#server-info-panel').hide(); 
	$('#server-info-panel-title').unbind('click').click(function () {
		openServerInfoPanel();
    });		
}


function openFavoriteBoardList()
{
	$('#favorite-board-list').show(); 
	$('#favorite-board-list-toolbar').show();
	$('#favorite-board-list-title').unbind('click').click(function () {
		closeFavoriteBoardList();
	});	
}

function closeFavoriteBoardList()
{
	$('#favorite-board-list').hide(); 
	$('#favorite-board-list-toolbar').hide();
	$('#favorite-board-list-title').unbind('click').click(function () {
		openFavoriteBoardList();
    });		
}

function openSpecialMenu()
{
	$('#special-menu').show(); 
	$('#special-menu-toolbar').show();
	$('#special-menu-title').unbind('click').click(function () {
		closeSpecialMenu();
	});	
}

function closeSpecialMenu()
{
	$('#special-menu').hide(); 
	$('#special-menu-toolbar').hide();
	$('#special-menu-title').unbind('click').click(function () {
		openSpecialMenu();
    });		
}

var previousLoadSpecialMenuRequest = null;
var loadSpecialMenuRequestCount = 0;

function loadSpecialMenu(refresh) {
    if (previousLoadSpecialMenuRequest && loadSpecialMenuRequestCount > 1) {
        previousLoadSpecialMenuRequest.abort();
        previousLoadSpecialMenuRequest = null;
    }
    ++loadSpecialMenuRequestCount;

	previousLoadSpecialMenuRequest = $.ajax({
		url:     '/api-get-special-menu-content?'
		       + (refresh                                                                   ? 'bubbles=1&refresh=1' :
		          $('#automatic-updates-for-special-menu-checkbox').is(':checked') ? 'question-mark=1'     :
		                                                                                      'question-mark=0'      ),
		async: true,
		success: function(result){
			$('#special-menu').html(result);
		},
		error:  function(result, textStatus, errorThrown){
		},
		complete: function(result, textStatus) {
			previousLoadSpecialMenuRequest = null;
		}
	});
}

function updateSpecialMenu()
{
    if (previousLoadSpecialMenuRequest) {
        previousLoadSpecialMenuRequest.abort();
        previousLoadSpecialMenuRequest = null;
    }
    loadSpecialMenuRequestCount = 0;
    loadSpecialMenu(false);
    loadSpecialMenu(true);
}

function openBBSMenu(innerBody, title, toolbar, service, refresh)
{
	$(innerBody).html('<p><br><br>' + ajaxSpinnerBars + '</p>');
  	$(innerBody).show()
  	$(toolbar).show()
	$(title).unbind('click').click(function () {
		closeBBSMenu(innerBody, title, toolbar, service);
	});		 
	
	$.ajax({ 
		url: '/api-get-bbs-menu-content?service=' + encodeURIComponent(service) + '&refresh=' + (refresh ? '1' : '0'),
		async: true,
		success: function(result){ 
			$(innerBody).html(result);
		},
		error:  function(result, textStatus, errorThrown){
			if (textStatus != "abort")
				$(innerBody).html('メニューの読み込みに<br>失敗しました。'); 
		}
	});
}

function closeBBSMenu(innerBody, title, toolbar, service)
{
	$(innerBody).html('').hide();
	$(toolbar).hide();
	$(title).unbind('click').click(function () {
		openBBSMenu(innerBody, title, toolbar, service, false);
    });		
}

function openDownloadStatusPanel()
{
	$('#download-status-panel').show(); 
	$('#download-status-panel-toolbar').show(); 
	$('#download-status-panel-title').unbind('click').click(function () {
		closeDownloadStatusPanel();
	});	
}

function closeDownloadStatusPanel()
{
	$('#download-status-panel').hide(); 
	$('#download-status-panel-toolbar').hide(); 
	$('#download-status-panel-title').unbind('click').click(function () {
		openDownloadStatusPanel();
    });		
}

function updateDownloadStatusPanel() {
	$.ajax({ 
		url: '/api-get-download-status', 
		async: true,
		success: function(result){ 
			$('#download-status-panel').html(result);
			setTimeout(updateDownloadStatusPanel, 10000);
		},
		error:  function(result, textStatus, errorThrown){
			setTimeout(updateDownloadStatusPanel, 10000);
		}
	});
}

function updateServerInfoPanel() {
	$.ajax({ 
		url: '/api-get-server-info', 
		async: true,
		success: function(result){ 
			$('#server-info-panel').html(result);
			setTimeout(updateServerInfoPanel, 1000);
		},
		error:  function(result, textStatus, errorThrown){
			setTimeout(updateServerInfoPanel, 1000);
		}
	});
}

function configureImageDownloading()
{
	$.ajax({ 
		url: '/api-configure-image-downloading?enable=' + ($('#automatic-downloading-checkbox').is(':checked') ? '1' : '0'),
		async: true,
		success: function(result){
			updateDownloadStatus();
		},
		error:  function(result, textStatus, errorThrown){
		}
	});
}



/*****************************/
/* CURRENT BOARD/THREAD LIST */
/*****************************/

var currentBoardURL = "";
var currentBoardName = "スレッド一覧";
var previousUpdateThreadListRequest = null;
var isCurrentBoardInFavoriteList = false;
var logListMode = false;

var boardTabs = [{boardURL: "", boardName: "スレッド一覧", threadList: null, position: 0, threadURLList: ""}];
var currentBoardTab = 0;
var maxBoardTabCount = 16;
var threadURLList = [];
var threadListItemList = [];

function updateStarForFavoriteBoard() {
	isCurrentBoardInFavoriteList = false;
	$('#star-for-favorite-board').hide();
	if (currentBoardURL) {
		$.ajax({ 
			url: '/api-is-favorite-board?board-url=' + encodeURIComponent(currentBoardURL),
			async: true, 
			success: function(result){
				isCurrentBoardInFavoriteList = (result == "true");
				if (isCurrentBoardInFavoriteList) {
					$('#star-for-favorite-board').show();;
				} else {
					$('#star-for-favorite-board').hide();;
				}
			},
			error:  function(result, textStatus, errorThrown){
			}
		});
	}
}

function toggleStarForFavoriteBoard() {
	if (!currentBoardURL)
		return;

	$.ajax({ 
		url: (isCurrentBoardInFavoriteList
				 ? ('/api-remove-favorite-board?board-url=' + encodeURIComponent(currentBoardURL))
				 : ('/api-add-favorite-board?board-url=' + encodeURIComponent(currentBoardURL))), 
		async: true,
		success: function(result) { 
			isCurrentBoardInFavoriteList = !isCurrentBoardInFavoriteList;
			if (isCurrentBoardInFavoriteList) {
				$('#star-for-favorite-board').show();
			} else {
				$('#star-for-favorite-board').hide();
			}
			loadFavoriteBoardList(false);
			loadFavoriteBoardList(true);
		},
		error:  function(result, textStatus, errorThrown){
		}
	});
}

function setBoardName(boardName, boardURL)
{
	currentBoardName = boardName;
	$('#active-board-name').html(escapeHTML(boardName));
}

function setLogListMode(val) {
	logListMode = val;
	$('#log-list-mode-checkbox').prop('checked', val);
}

function setEventHandlersForThreadList() {
	jQuery.each(threadListItemList, function(index, value) {
    	$('#' + value.id)
     		.off('mousedown')
			.mousedown(function(event) {
     			if (event.which == 1) {
     				updateThreadContent(value.title, value.url);
					return false;
				} else if (event.which == 2) {
					event.preventDefault();
     				updateThreadContent(value.title, value.url, '', '', true);
					return false;
				} else if (event.which == 3) {
					displayThreadMenu(event, value.url);
					return false;
				}
				return true;
			})
			.attr('oncontextmenu', 'return false;')
	});
}

window.updateThreadList = function(boardName, boardURL, searchText, searchType, newTab, switchTabs, newLogListMode)
{
	if (typeof searchText == 'undefined') searchText = "";
	if (typeof searchType == 'undefined') searchType = "";
	if (typeof newTab     == 'undefined') newTab     = false;
	if (typeof switchTabs == 'undefined') switchTabs = true;
	if (typeof newLogListMode == 'undefined') newLogListMode = false;
	
	if (!displayThreadList)
		List();
	if (newTab) {
		addBoardTab();
	} else if (switchTabs) {
		switchToRightBoardTab(boardName, boardURL);
	}
	
	$('#thread-list').html(ajaxSpinnerBars);
	if (boardName) {
		setBoardName(boardName, boardURL); 
	} else {
		setBoardName('スレッド一覧', boardURL); 
	}
	currentBoardURL = boardURL;
	$('#board-url-text-field').val(boardURL);
	updateStarForFavoriteBoard();
	threadURLList = [];
	threadListItemList = [];
	setLogListMode(newLogListMode);
	
	if (previousUpdateThreadListRequest)
		previousUpdateThreadListRequest.abort();
	
	previousUpdateThreadListRequest = $.ajax({ 
		url: '/api-get-thread-list?board-url=' + boardURL
		+ '&search-text=' + encodeURIComponent(searchText)
		+ '&search-type=' + encodeURIComponent(searchType)
		+ (MSIEVersion() > 0 ? "&ie=1" : "")
		+ (logListMode ? "&log-list=1" : ""), 
		async: true,
		success: function(result){ 
			$('#thread-list').html(result); 
			$('#thread-list script:not(.keep)').remove();
			adjustComponents();
			setEventHandlersForThreadList();
			loadFavoriteBoardList(true);
		},
		error:  function(result, textStatus, errorThrown){
			if (textStatus == "abort") {
				$('#thread-list').html(''); 
			} else {
				$('#thread-list').html('&nbsp;スレッド一覧の読み込みに失敗しました。'); 
			}
		}
	});
}

function toggleLogListMode() {
	if (!currentBoardURL || currentBoardURL.length <= 0) 
		return;
	
	setLogListMode(!logListMode);
	console.log("logListMode: " + logListMode);
	updateThreadList(currentBoardName, currentBoardURL, "", "", false, false, logListMode);
}

function loadSimilarThreadList()
{
	var threadUrl = (currentThreadURL ? currentThreadURL : getFirstVisiblePostThreadUrl());
	
	if (!threadUrl || threadUrl.length <= 0) 
		return;
	
	if (!displayThreadList)
		toggleThreadList();
	// switchToRightBoardTab(boardName, boardURL);
	
	$('#thread-list').html(ajaxSpinnerBars);
	setBoardName('似スレ一覧'); 
	currentBoardURL = null;
	$('#board-url-text-field').val("");
	updateStarForFavoriteBoard();
	threadURLList = [];
	threadListItemList = [];
	setLogListMode(false);
	
	if (previousUpdateThreadListRequest)
		previousUpdateThreadListRequest.abort();
	
	previousUpdateThreadListRequest = $.ajax({ 
		url: '/api-get-similar-thread-list?thread-url=' + encodeURIComponent(threadUrl)
		+ (MSIEVersion() > 0 ? "&ie=1" : ""), 
		async: true,
		success: function(result){ 
			$('#thread-list').html(result); 
			$('#thread-list script:not(.keep)').remove();
	        adjustComponents();
			setEventHandlersForThreadList();
		},
		error:  function(result, textStatus, errorThrown){
			if (textStatus == "abort") {
				$('#thread-list').html(''); 
			} else {
				$('#thread-list').html('&nbsp;似スレ一覧の読み込みに失敗しました。'); 
			}
		}
	});
}

function loadSpecialThreadList(listName, api, refresh, searchText, searchType, newTab, switchTabs) {
	if (typeof searchText == 'undefined') searchText = "";
	if (typeof searchType == 'undefined') searchType = "";
	if (typeof newTab     == 'undefined') newTab     = false;
	if (typeof switchTabs == 'undefined') switchTabs = true;
	
	if (!displayThreadList)
		toggleThreadList();
	if (newTab) {
		addBoardTab();
	} else if (switchTabs) {
		switchToRightBoardTab(listName, null);
	}
	
	$('#thread-list').html(ajaxSpinnerBars);
	setBoardName(listName, null); 
	currentBoardURL = null;
	$('#board-url-text-field').val('');
	updateStarForFavoriteBoard();
	threadURLList = [];
	threadListItemList = [];
	setLogListMode(false);
  	
	if (previousUpdateThreadListRequest)
		previousUpdateThreadListRequest.abort();
	
	previousUpdateThreadListRequest = $.ajax({ 
		url: './' + api
		+ '?search-text=' + encodeURIComponent(searchText)
		+ '&search-type=' + encodeURIComponent(searchType)
		+ (MSIEVersion() > 0 ? "&ie=1" : "")
		+ (refresh ? '&refresh=1' : ''), 
		async: true,
		success: function(result){ 
			$('#thread-list').html(result); 
			adjustComponents();
			setEventHandlersForThreadList();
		},
		error:  function(result, textStatus, errorThrown){
			if (textStatus == "abort") {
				$('#thread-list').html(''); 
				$('#thread-list script:not(.keep)').remove();
			} else {
				$('#thread-list').html('&nbsp;' + listName + 'の読み込みに失敗しました。'); 
			}
		}
	});
}

function loadFavoriteThreadList(refresh, searchText, searchType, newTab, switchTabs) {
	if (typeof searchText == 'undefined') searchText = "";
	if (typeof searchType == 'undefined') searchType = "";
	if (typeof newTab     == 'undefined') newTab     = false;
	if (typeof switchTabs == 'undefined') switchTabs = true;
	
	loadSpecialThreadList('お気にスレ', 'api-get-favorite-thread-list', refresh, searchText, searchType, newTab, switchTabs);
}

function loadRecentlyViewedThreadList(refresh, searchText, searchType, newTab, switchTabs) {
	if (typeof searchText == 'undefined') searchText = "";
	if (typeof searchType == 'undefined') searchType = "";
	if (typeof newTab     == 'undefined') newTab     = false;
	if (typeof switchTabs == 'undefined') switchTabs = true;
	
	loadSpecialThreadList('最近読んだスレ', 'api-get-recently-viewed-thread-list', refresh, searchText, searchType, newTab, switchTabs);
}

function loadRecentlyPostedThreadList(refresh, searchText, searchType, newTab, switchTabs) {
	if (typeof searchText == 'undefined') searchText = "";
	if (typeof searchType == 'undefined') searchType = "";
	if (typeof newTab     == 'undefined') newTab     = false;
	if (typeof switchTabs == 'undefined') switchTabs = true;
	
	loadSpecialThreadList('書込履歴', 'api-get-recently-posted-thread-list', refresh, searchText, searchType, newTab, switchTabs);
}

function loadDatFileList(refresh, searchText, searchType, newTab, switchTabs) {
	if (typeof searchText == 'undefined') searchText = "";
	if (typeof searchType == 'undefined') searchType = "";
	if (typeof newTab     == 'undefined') newTab     = false;
	if (typeof switchTabs == 'undefined') switchTabs = true;
	
	loadSpecialThreadList('DATファイル一覧', 'api-get-dat-file-list', refresh, searchText, searchType, newTab, switchTabs);
}

function loadHtmlFileList(refresh, searchText, searchType, newTab, switchTabs) {
	if (typeof searchText == 'undefined') searchText = "";
	if (typeof searchType == 'undefined') searchType = "";
	if (typeof newTab     == 'undefined') newTab     = false;
	if (typeof switchTabs == 'undefined') switchTabs = true;
	
	loadSpecialThreadList('HTMLファイル一覧', 'api-get-html-file-list', refresh, searchText, searchType, newTab, switchTabs);
}

window.refreshThreadList = function() {
	if (currentBoardURL) {
		updateThreadList(currentBoardName, currentBoardURL);
	} else if (currentBoardName == 'お気にスレ') {
		loadFavoriteThreadList(true);
	} else if (currentBoardName == '最近読んだスレ') {
		loadRecentlyViewedThreadList(true);
	} else if (currentBoardName == '書込履歴') {
		loadRecentlyPostedThreadList(true);
	} else if (currentBoardName == 'DATファイル一覧') {
		loadDatFileList(true);	
	} else if (currentBoardName == 'HTMLファイル一覧') {
		loadHtmlFileList(true);
	}
}

function saveCurrentBoardTab() {
	boardTabs[currentBoardTab].boardURL      = currentBoardURL;
	boardTabs[currentBoardTab].boardName     = currentBoardName;
	boardTabs[currentBoardTab].threadURLList = threadURLList;
	boardTabs[currentBoardTab].logListMode   = logListMode;
	console.log("logListMode: " + logListMode);
	// boardTabs[currentBoardTab].threadList = $('#thread-list').html();
	
	if ($('#thread-list-fixed-table-container').length <= 0) {
		boardTabs[currentBoardTab].threadList = null;
		
	} else {
		boardTabs[currentBoardTab].position = $('#thread-list .fixed-table-container-inner').scrollTop() / $('#thread-list tbody').height()
		var container = $('#thread-list-fixed-table-container').detach();
		$('#thread-list').html('');
		boardTabs[currentBoardTab].threadList = container;
	}
}

function loadCurrentBoardTab() {
	currentBoardURL  = boardTabs[currentBoardTab].boardURL;
	currentBoardName = boardTabs[currentBoardTab].boardName;
	threadURLList    = boardTabs[currentBoardTab].threadURLList;
	setLogListMode(boardTabs[currentBoardTab].logListMode);
	$('#board-url-text-field').val(currentBoardURL);
	updateStarForFavoriteBoard();

	$('#thread-list').html('');
	if (boardTabs[currentBoardTab].threadList) {
		$('#thread-list').append(boardTabs[currentBoardTab].threadList);
		setTimeout(function() {
			setThreadListFixedTableContainerHeight();
			setTimeout(function() {
				$('#thread-list .fixed-table-container-inner').scrollTop(boardTabs[currentBoardTab].position * $('#thread-list tbody').height()); 
			}, 0);
		}, 0);
	}
}

function loadThreadListIfEmpty() {
	if ($('#thread-list').html() == null || $('#thread-list').html() == '') {
		if (currentBoardURL && currentBoardURL.length > 0) {
			updateThreadList(null, currentBoardURL, '', '', false, false, logListMode);
		} else if (currentBoardName == 'お気にスレ') {
			loadFavoriteThreadList(false, '', '', false, false);
		} else if (currentBoardName == '最近読んだスレ') {
			loadRecentlyViewedThreadList(false, '', '', false, false);
		} else if (currentBoardName == '書込履歴') {
			loadRecentlyPostedThreadList(false, '', '', false, false);
		}
	}
}

function switchBoardTabs(newTab) {
	// alert(newTab + ', ' + currentBoardTab);
	if (newTab == currentBoardTab)
		return;
	
	if (previousUpdateThreadListRequest)
		previousUpdateThreadListRequest.abort();

	saveCurrentBoardTab();
	currentBoardTab = newTab;
	loadCurrentBoardTab();
	adjustComponents();
	
	loadThreadListIfEmpty();
}

function boardNamesStartDragging(event, ui, index) {
	ui.helper.css("z-index", 1);
	ui.helper.css("-moz-box-shadow", "none");
	ui.helper.css("-webkit-box-shadow", "none");
	ui.helper.css("box-shadow", "none");
	ui.helper.css("opacity", "0.80");
}

function boardNamesStopDragging(event, ui, index) {
	var newIndex = Math.floor((ui.offset.left - $('#board-names').offset().left - parseInt($('#board-names').css('border-left-width').replace('px', ""))) / ui.helper.outerWidth(true));
	if (   ui.offset.left > $('#board-names').offset().left + parseInt($('#board-names').css('border-left-width').replace('px', ""))
	    && newIndex < index)
		++newIndex;	
	if (newIndex <= 0)
		newIndex = 0;
	if (newIndex >= boardTabs.length)
		newIndex = boardTabs.length - 1;
	saveCurrentBoardTab();
 	boardTabs.splice(newIndex, 0, boardTabs.splice(index, 1)[0]);
	currentBoardTab = newIndex;
	loadCurrentBoardTab();
	updateBoardNames();
}

function updateBoardNames() {
	var newTabs = "";
	var boardNameWidth = ($('#board-names').width() - $('#add-board-tab-button').outerWidth(true)) / boardTabs.length
	                      - ($('#active-board-name').outerWidth(true) - $('#active-board-name').width());
	var boardNameMinWidth = parseInt($('#active-board-name').css('min-width').replace('px', ""))
	var displayInactiveTabs = true;
	
	if (boardNameWidth < boardNameMinWidth) {
		displayInactiveTabs = false;
		boardNameWidth = ($('#board-names').width() - $('#add-board-tab-button').outerWidth(true))
	                      - ($('#active-board-name').outerWidth(true) - $('#active-board-name').width());
	}
	
	jQuery.each(boardTabs, function (index, value) {
		var closeBoardTabButton = '<img class="close-tab-button" src="/img/close-tab-14x14.png" width=14 height=14 onmousedown="event.stopPropagation(); closeBoardTab(event, ' + index + ');" style="position: absolute; left: ' + (boardNameWidth + 13) + 'px; top: 6px;">';
		var newTabID;
			
		if (index == currentBoardTab) {
			newTabID = "active-board-name";
			newTabs = newTabs + '<div id="' + newTabID + '" class="board-name" style="width: ' + boardNameWidth + 'px;">' + escapeHTML(currentBoardName)
			                  + closeBoardTabButton
							  + '</div>'
		} else if (displayInactiveTabs) {
			newTabID = "board-name-" + index;
			newTabs = newTabs + '<div id="' + newTabID + '" class="board-name inactive" style="width: ' + boardNameWidth + 'px;">' + escapeHTML(value.boardName)
			                  + closeBoardTabButton
							  + '</div>'
		}
		if (displayInactiveTabs)
			newTabs = newTabs + '<script>$("#" + "' + newTabID + '").draggable({'
							  + 'containment: "#board-names", '
							  + 'axis: "x",'
							  + 'start: function (event, ui) { boardNamesStartDragging(event, ui, ' + index + ')},'
							  + 'stop: function (event, ui) { boardNamesStopDragging(event, ui, ' + index + ')} '
							  + '}).'
							  + 'click(function (event, ui) { switchBoardTabs(' + index + '); });'
							  + '</script>';		

	});
	
	newTabs = newTabs + '<div id="add-board-tab-button" onclick="addBoardTab();">+</div>';
	newTabs = newTabs + '<div style="clear: both;"></div>';
	
	$('#board-names').html(newTabs);
}

function addBoardTab () {
	if (boardTabs.length >= maxBoardTabCount)
		return;

	if (previousUpdateThreadListRequest)
		previousUpdateThreadListRequest.abort();

	saveCurrentBoardTab();
	
	currentBoardURL  = "";
	currentBoardName = "スレッド一覧";
	threadURLList    = ""
	$('#thread-list').html("");
	$('#board-url-text-field').val("");
	$('#thread-search-text-field').val("");
	updateStarForFavoriteBoard();
	setLogListMode(false);

	boardTabs.push({boardURL: currentBoardURL, boardName: currentBoardName, threadList: null, position: 0});
	currentBoardTab = boardTabs.length - 1;
	
	adjustComponents();
}

function closeBoardTab(event, index) {
	event.stopPropagation();
	if (boardTabs.length <= 1) {
		currentBoardURL  = "";
		currentBoardName = "スレッド一覧";
		threadURLList    = ""
		$('#thread-list').html("");
		$('#board-url-text-field').val("");
		$('#thread-search-text-field').val("");
		updateStarForFavoriteBoard();
		setLogListMode(false);
		saveCurrentBoardTab();
	} else {
		saveCurrentBoardTab();
		boardTabs.splice(index, 1);
		if (currentBoardTab >= boardTabs.length)
			--currentBoardTab;
		loadCurrentBoardTab();
	}
	adjustComponents();
	loadThreadListIfEmpty();
}

function switchToRightBoardTab(boardName, boardURL) {
	var newTab = -1;
	
	// saveCurrentBoardTab();
	
	if (boardURL == null|| boardURL == "") {
		jQuery.each(boardTabs, function (index, value) {
			if (value.boardName == boardName)
				newTab = index;
		});
	} else {
		jQuery.each(boardTabs, function (index, value) {
			if (value.boardURL == boardURL)
				newTab = index;
		});
	}
	if (newTab >= 0) {
		switchBoardTabs(newTab);
		return;
	}
	
	if (boardTabs.length >= maxBoardTabCount || currentBoardName == "スレッド一覧")
		return;

	return;
}

function openOriginalBoard() {
	if (currentBoardURL)
		open(currentBoardURL);
}

var openBoardNameWindowWidth  = 428;
var openBoardNameWindowHeight = 119;

window.setBoardNameWindowSize = function (width, height) {
	// alert(width + ', ' + height);
	openBoardNameWindowWidth  = width;
	openBoardNameWindowHeight = height;
}

function openBoardNameWindow(boardURL, server, service, board) {
	var boardNameWindow = open('/board-name-page?board-url=' + boardURL + '&server=' + server + '&service=' + service + '&board=' +  board,
						        randomElementID(),
							    centerPopupWindow(openBoardNameWindowWidth, openBoardNameWindowHeight) + ", resizable  = no" + ", scrollbars = no");
	boardNameWindow.focus();
}

function displayBoardMenu(event, boardURL, server, service, board) {
	var id = ('board-menu-' + service + '-' + board).replace(/[/.]/g, "_");

	if ($('#' + id).length > 0 || !doesWindowHaveFocus)
		return;
	
	var menuDiv = createPopupMenu(event, id);

	menuDiv.append($("<div />")
	                   .attr("class", "popup-menu-item")
					   .attr("onmousedown", "updateThreadList(null, '" + boardURL + "', '', '', false); $(event.target).parent().remove();")
					   .css('background-color', '#C7D7F1')
					   .html("開く"));
	
	menuDiv.append($("<div />")
	                   .attr("class", "popup-menu-item")
					   .attr("onmousedown", "updateThreadList(null, '" + boardURL + "', '', '', true); $(event.target).parent().remove();")
					   .html("新しいタブで開く"));
				  
	menuDiv.append($("<div />")
	                   .attr("class", "popup-menu-item")
					   .attr("onmousedown", "loadNewPostsInBoard(null, '" + boardURL + "'); $(event.target).parent().remove();")
					   .html("新着まとめ読み"));
				  
	menuDiv.append($("<div />")
	                   .attr("class", "popup-menu-item")
					   .attr("onmousedown", "openBoardNameWindow('" + boardURL + "', '" + server + "', '" + service + "', '" +  board + "'); $(event.target).parent().remove();")
					   .html("板の名前を設定"));
				  
	$("#popup-menus").append(menuDiv);
	$('.popup-menu-item').hover(function(event) { $(this).css('background-color', event.type === 'mouseenter' ? '#C7D7F1' : 'transparent'); });
	
	keepElementWithinWindow('#' + id);
}

function displaySpecialThreadListMenu(event, id, loadFunction) {
	if ($('#' + id).length > 0 || !doesWindowHaveFocus)
		return;
	
	var menuDiv = createPopupMenu(event, id);

	menuDiv.append($("<div />")
	                   .attr("class", "popup-menu-item")
					   .attr("onmousedown", loadFunction + "(false); $(event.target).parent().remove();")
					   .css('background-color', '#C7D7F1')
					   .html("開く"));
	
	menuDiv.append($("<div />")
	                   .attr("class", "popup-menu-item")
					   .attr("onmousedown", loadFunction + "(false, '', '', true); $(event.target).parent().remove();")
					   .html("新しいタブで開く"));
				  
	$("#popup-menus").append(menuDiv);
	$('.popup-menu-item').hover(function(event) { $(this).css('background-color', event.type === 'mouseenter' ? '#C7D7F1' : 'transparent'); });
	
	keepElementWithinWindow('#' + id);
}

function displayFavoriteThreadListMenu(event) {
	displaySpecialThreadListMenu(event, 'favorite-thread-list-menu', 'loadFavoriteThreadList');
}

function displayRecentlyViewedThreadListMenu(event) {
	displaySpecialThreadListMenu(event, 'recently-viewed-thread-list-menu', 'loadRecentlyViewedThreadList');
}

function displayRecentlyPostedThreadListMenu(event) {
	displaySpecialThreadListMenu(event, 'recently-posted-thread-list-menu', 'loadRecentlyPostedThreadList');
}

function displayDatFileListMenu(event) {
	displaySpecialThreadListMenu(event, 'dat-file-list-menu', 'loadDatFileList');
}

function displayHtmlFileListMenu(event) {
	displaySpecialThreadListMenu(event, 'html-file-list-menu', 'loadHtmlFileList');
}

function updateNewPostCounts() {
	if (   $('#automatic-updates-for-thread-list-checkbox').is(':checked')
	    && threadURLList.length <= thresholdForUpdatingNewPostCounts) {
		var threadURLListInPlainText = "";
		
		$(threadURLList).each(function(index, element) {
			if (index)
				threadURLListInPlainText = threadURLListInPlainText + '\n';
			threadURLListInPlainText = threadURLListInPlainText + element;
		});
		
		// console.log(threadURLListInPlainText);
		
		if (threadURLListInPlainText != "") {
			$.ajax({ 
				url: '/api-get-new-post-counts',
				async: true,
				type: 'POST',
				data: {'thread-url-list': threadURLListInPlainText},
				success: function(result){ 
					$('#temporary-script').html(result);
				},
				error:  function(result, textStatus, errorThrown){
				},
				complete: function(result, textStatus) {
				}
			});
		}
	}
	
	setTimeout(updateNewPostCounts, 60000);
}

$(document).ready(function() {
	updateNewPostCounts();
});



/*******************/
/* CURRENT THREAD  */
/*******************/

var currentThreadURL = null;
var currentThreadTitle = null;
var previousUpdateThreadContentRequest = null;
var isCurrentThreadInFavoriteList = false;

var unloadedThumbnailList = [];
var loadedThumbnailList = [];
var imageList = [];
var reverseAnchors = [];

var threadHistory = [];
var threadHistoryForward = [];
var threadHistoryMaxLength = 10;

var threadTabs = [{threadURL: "", threadTitle: "スレッド", threadContent: "", threadHistory: [], threadHistoryForward: [], position: 0, imageList: [], thumbnailList: [], reverseAnchors: []}];
var currentThreadTab = 0;
var maxThreadTabCount = 16;


function updateStarForFavoriteThread() {
	isCurrentThreadInFavoriteList = false;
	$('#star-for-favorite-thread').hide();
	if (currentThreadURL) {
		$.ajax({ 
			url: '/api-is-favorite-thread?thread-url=' + encodeURIComponent(currentThreadURL),	
			async: true, 
			success: function(result){
				isCurrentThreadInFavoriteList = (result == "true");
				if (isCurrentThreadInFavoriteList) {
					$('#star-for-favorite-thread').show();;
				} else {
					$('#star-for-favorite-thread').hide();;
				}
			},
			error:  function(result, textStatus, errorThrown){
			}
		});
	}
}

function toggleStarForFavoriteThread() {
	if (!currentThreadURL)
		return;

	$.ajax({ 
		url: (isCurrentThreadInFavoriteList
				 ? ('/api-remove-favorite-thread?thread-url=' + encodeURIComponent(currentThreadURL))
				 : ('/api-add-favorite-thread?thread-url=' + encodeURIComponent(currentThreadURL))), 
		async: true,
		success: function(result){ 
			isCurrentThreadInFavoriteList = !isCurrentThreadInFavoriteList;
			if (isCurrentThreadInFavoriteList) {
				$('#star-for-favorite-thread').show();;
			} else {
				$('#star-for-favorite-thread').hide();;
			}
		},
		error:  function(result, textStatus, errorThrown){
		}
	});
}

function threadContentGoForward() {
	
	if (threadHistoryForward.length > 0) {
		var pageData = threadHistoryForward.pop();
	
		threadHistory.push({title: currentThreadTitle,
		                    URL: currentThreadURL,
							position: $('#thread-content').scrollTop() / $('#thread-content-wrapper').height(),
							content: $('#thread-content-wrapper').detach(),
							imageList: imageList,
							thumbnailList: loadedThumbnailList.concat(unloadedThumbnailList)});
		
		$('#thread-content').html('').append(pageData.content);
		currentThreadTitle = pageData.title;
		currentThreadURL = pageData.URL;
		$('#thread-url-text-field').val(pageData.URL);
		imageList = pageData.imageList;
		unloadedThumbnailList = pageData.thumbnailList;
		loadedThumbnailList = [];
		loadThumbnails();
		
		setThreadTitle(currentThreadTitle, currentThreadURL);
		updateStarForFavoriteThread();
		adjustComponents();
		setTimeout(function () { $('#thread-content').scrollTop(pageData.position * $('#thread-content-wrapper').height()); }, 0);
	}
}

function threadContentGoBackward() {
	
	if (threadHistory.length > 0) {
		var pageData = threadHistory.pop();
		
		threadHistoryForward.push({title: currentThreadTitle,
		                           URL: currentThreadURL,
								   position: $('#thread-content').scrollTop() / $('#thread-content-wrapper').height(),
								   content: $('#thread-content-wrapper').detach(),
								   imageList: imageList,
								   thumbnailList: loadedThumbnailList.concat(unloadedThumbnailList)});
		
		$('#thread-content').html('').append(pageData.content);
		currentThreadTitle = pageData.title;
		currentThreadURL = pageData.URL;
		$('#thread-url-text-field').val(pageData.URL);
		imageList = pageData.imageList;
		unloadedThumbnailList = pageData.thumbnailList;
		loadedThumbnailList = [];
		loadThumbnails();
				
		setThreadTitle(currentThreadTitle, currentThreadURL);
		updateStarForFavoriteThread();
		adjustComponents();
		setTimeout(function () { $('#thread-content').scrollTop(pageData.position * $('#thread-content-wrapper').height()); }, 0);
	}
}	

function setThreadTitle(threadTitle, threadURL)
{
	currentThreadTitle = threadTitle;
	
	$('#active-thread-title').html(escapeHTML(threadTitle));
}

function jumpToPost() {} // dummy

window.updateThreadContent = function(threadTitle, threadURL, searchText, searchType, newTab, switchTabs, forceCompleteReload) {
	// console.log('updateThreadContent()');
	
	if (typeof searchText == 'undefined') searchText = "";
	if (typeof searchType == 'undefined') searchType = "";
	if (typeof newTab     == 'undefined') newTab     = false;
	if (typeof switchTabs == 'undefined') switchTabs = true;
	if (typeof forceCompleteReload == 'undefined') forceCompleteReload = false;
	
	if (newTab) {
		addThreadTab();
	} else if (switchTabs) {
		switchToRightThreadTab(threadTitle, threadURL);
	}
	
	if (   false
	    && (currentThreadURL || currentThreadTitle)
	    && ((!threadURL && !currentThreadURL) || threadURL != currentThreadURL)) {
		threadHistory.push({title: currentThreadTitle,
							URL: currentThreadURL,
							position: $('#thread-content').scrollTop() / $('#thread-content-wrapper').height(),
							content: $('#thread-content-wrapper').detach(),
							imageList: imageList,
							thumbnailList: loadedThumbnailList.concat(unloadedThumbnailList)});
		threadHistoryForward = [];
		if (threadHistory.length > threadHistoryMaxLength)
			threadHistory.shift();
	}
	
	var appendNewPosts =    !forceCompleteReload
	                     && currentThreadURL
	                     && (currentThreadURL == threadURL) 
						 && currentThreadURL.match(/\/$/) 
						 && $('.post-heading').last().length > 0
						 && searchText.length <= 0
						 && $('#thread-content .highlighted').length <= 0;
	var lastPostIndex;
	
	if (appendNewPosts) {
		$('#thread-content-wrapper').append($('<div />')
		                                        .attr('id', "ajax-spinner-bars-container")
												.css('margin-left', '12px')
												.css('width', '32px')
												.css('height', '32px')
												.css('position', 'relative')
												.html(ajaxSpinnerBars));
		$('#thread-content').scrollTop($('#thread-content-wrapper').height());
		lastPostIndex = $('.post-heading').last().attr('post-index');
	} else {
		$('#thread-content').html(ajaxSpinnerBars);
	}
	
	if (threadTitle) {
		setThreadTitle(threadTitle, threadURL); 
	} else {
		setThreadTitle("スレッド", null)
	}
	currentThreadURL = threadURL;
	$('#thread-url-text-field').val(threadURL);
	updateStarForFavoriteThread();
	adjustComponents();
	
	if (previousUpdateThreadContentRequest)
		previousUpdateThreadContentRequest.abort();
	removeAllFloatingPosts();
	removeAllPopupMenus();

	$('.new-post-index').removeClass('new-post-index');
	if (appendNewPosts) {
		$('.reverse-anchors-container').remove();
	}
	
	previousUpdateThreadContentRequest = $.ajax({ 
		url: '/api-get-thread-content?thread-url=' + encodeURIComponent(threadURL)
		+ '&search-text=' + encodeURIComponent(searchText)
		+ '&search-type=' + encodeURIComponent(searchType)
		+ (appendNewPosts ? ('&append-after=' + lastPostIndex) : ''), 
		async: true,
		success: function(result) { 
			// $('#thread-content').html(ajaxSpinnerBarsRed);
			// setTimeout(function() {
				if (appendNewPosts) {
					$('#thread-content-wrapper > #ajax-spinner-bars-container').remove();
					$('#thread-content-wrapper > .message-error-right-pane').remove();
					$('#thread-content-wrapper').append(result); 
				} else {
					$('#thread-content').html(result); 
				}
				$('#thread-content script:not(.keep)').remove();
				adjustComponents();
				loadFavoriteBoardList(true);
				setTimeout(function () {
					jumpToPost(); // jumpToPost() is defined in result
					// $('#thread-content-run-once').html("");
				}, 250);
			// }, 0);
		},
		error:  function(result, textStatus, errorThrown){
			if (appendNewPosts)
				$('#ajax-spinner-bars-container').remove();
			if (textStatus != "abort") {
				$('#thread-content').html('スレッドの読み込みに失敗しました。'); 
				// $('#active-thread-title').html("スレッド");
				// if (!threadTitle)
				//	currentThreadTitle = null;
			}
		}
	});
}

function loadNewPosts(appURL) {
	// console.log('loadNewPostsInBoard()');
	var threadTitle = '新着まとめ';
	
	if (currentThreadTitle != 'スレッド' || currentThreadURL != null)
		addThreadTab();
	// switchToRightThreadTab(threadTitle, null)
	
	if (false && (currentThreadURL || currentThreadTitle)) {
		threadHistory.push({title: currentThreadTitle,
							URL: currentThreadURL,
							position: $('#thread-content').scrollTop() / $('#thread-content-wrapper').height(),
							content: $('#thread-content-wrapper').detach(),
							imageList: imageList,
							thumbnailList: loadedThumbnailList.concat(unloadedThumbnailList)});
		threadHistoryForward = [];
		if (threadHistory.length > threadHistoryMaxLength)
			threadHistory.shift();
	}
	
	$('#thread-content').html(ajaxSpinnerBars);
	setThreadTitle(threadTitle, null)
	currentThreadURL = '';
	$('#thread-url-text-field').val('');
	updateStarForFavoriteThread();
	adjustComponents();
	
	if (previousUpdateThreadContentRequest)
		previousUpdateThreadContentRequest.abort();
	removeAllFloatingPosts();
	removeAllPopupMenus();
	
	previousUpdateThreadContentRequest = $.ajax({ 
		url: appURL, 
		async: true,
		success: function(result) { 
			$('#thread-content').html(result); 
			$('#thread-content script:not(.keep)').remove();
			adjustComponents();
			loadFavoriteBoardList(true);
			setTimeout(function () {
				jumpToPost(); // jumpToPost() is defined in result
				// $('#thread-content-run-once').html("");
			}, 250);
		},
		error:  function(result, textStatus, errorThrown){
			if (textStatus != "abort") {
				$('#thread-content').html('新着まとめの読み込みに失敗しました。'); 
			}
		}
	});
}

function loadNewPostsInBoard(boardName, boardURL) {
	loadNewPosts('/api-get-new-posts-in-board?board-url=' + encodeURIComponent(boardURL));
}

function loadNewPostsInFavoriteThreads() {
	loadNewPosts('/api-get-new-posts-in-favorite-threads');
}

function loadNewPostsInRecentlyViewedThreads() {
	loadNewPosts('/api-get-new-posts-in-recently-viewed-threads');
}

function loadNewPostsInRecentlyPostedThreads(boardName, boardURL) {
	loadNewPosts('/api-get-new-posts-in-recently-posted-threads');
}

function reloadCurrentThread() {
	if (currentThreadURL)
		updateThreadContent(null, removeOptionsFromThreadURL(currentThreadURL));
}

function saveCurrentThreadTab() {
	threadTabs[currentThreadTab].threadURL   = currentThreadURL;
	threadTabs[currentThreadTab].threadTitle  = currentThreadTitle;
	threadTabs[currentThreadTab].threadHistory  = threadHistory;
	threadTabs[currentThreadTab].threadHistoryForward  = threadHistoryForward;
	threadTabs[currentThreadTab].position = $('#thread-content').scrollTop() / $('#thread-content-wrapper').height();
	threadTabs[currentThreadTab].imageList = imageList;
	threadTabs[currentThreadTab].thumbnailList = loadedThumbnailList.concat(unloadedThumbnailList);
	threadTabs[currentThreadTab].reverseAnchors = reverseAnchors;
	
	threadTabs[currentThreadTab].threadContent = $('#thread-content-wrapper').detach();
	$('#thread-content').html('');
}

function loadCurrentThreadTab() {
	currentThreadURL = threadTabs[currentThreadTab].threadURL;
	currentThreadTitle = threadTabs[currentThreadTab].threadTitle;
	threadHistory = threadTabs[currentThreadTab].threadHistory;
	threadHistoryForward = threadTabs[currentThreadTab].threadHistoryForward;
	$('#thread-url-text-field').val(currentThreadURL);
	imageList = threadTabs[currentThreadTab].imageList;
	unloadedThumbnailList = threadTabs[currentThreadTab].thumbnailList;
	loadedThumbnailList = [];
	loadThumbnails();
	reverseAnchors = threadTabs[currentThreadTab].reverseAnchors;
	updateStarForFavoriteThread();
	
	$('#thread-content').html('');
	if (threadTabs[currentThreadTab].threadContent)
		$('#thread-content').append(threadTabs[currentThreadTab].threadContent);
	setTimeout(function () { $('#thread-content').scrollTop(threadTabs[currentThreadTab].position * $('#thread-content-wrapper').height()); }, 0);
}

function loadThreadContentIfEmpty() {
	if ($('#thread-content').html() == '' && currentThreadURL)
		updateThreadContent(null, currentThreadURL, '', '', false, false);
}

function switchThreadTabs(newTab) {
	if (newTab == currentThreadTab)
		return;
	
	if (previousUpdateThreadContentRequest)
		previousUpdateThreadContentRequest.abort();

	saveCurrentThreadTab();
	currentThreadTab = newTab;
	loadCurrentThreadTab();
	adjustComponents();

	loadThreadContentIfEmpty();
	adjustComponents();
}

function threadTitlesStartDragging(event, ui, index) {
	ui.helper.css("z-index", 1);
	ui.helper.css("-moz-box-shadow", "none");
	ui.helper.css("-webkit-box-shadow", "none");
	ui.helper.css("box-shadow", "none");
	ui.helper.css("opacity", "0.80");
}

function threadTitlesStopDragging(event, ui, index) {
	var newIndex = Math.floor((ui.offset.left - $('#thread-titles').offset().left - parseInt($('#thread-titles').css('border-left-width').replace('px', ""))) / ui.helper.outerWidth(true));
	if (   ui.offset.left > $('#thread-titles').offset().left + parseInt($('#thread-titles').css('border-left-width').replace('px', ""))
	    && newIndex < index)
		++newIndex;	
	if (newIndex <= 0)
		newIndex = 0;
	if (newIndex >= threadTabs.length)
		newIndex = threadTabs.length - 1;
	saveCurrentThreadTab();
 	threadTabs.splice(newIndex, 0, threadTabs.splice(index, 1)[0]);
	currentThreadTab = newIndex;
	loadCurrentThreadTab();
	updateThreadTitles();
}

function updateThreadTitles() {
	var newTabs = "";
	var threadTitleWidth = ($('#thread-titles').width() - $('#add-thread-tab-button').outerWidth(true)) / threadTabs.length
	                      - ($('#active-thread-title').outerWidth(true) - $('#active-thread-title').width());
	var threadTitleMinWidth = parseInt($('#active-thread-title').css('min-width').replace('px', ""))
	var displayInactiveTabs = true;
	
	if (threadTitleWidth < threadTitleMinWidth) {
		displayInactiveTabs = false;
		threadTitleWidth = ($('#thread-titles').width() - $('#add-thread-tab-button').outerWidth(true))
	                      - ($('#active-thread-title').outerWidth(true) - $('#active-thread-title').width());
	}
	
	jQuery.each(threadTabs, function (index, value) {
		var closeThreadTabButton = '<img class="close-tab-button" src="/img/close-tab-14x14.png" width=14 height=14 onmousedown="event.stopPropagation(); closeThreadTab(event, ' + index + ');" style="position: absolute; left: ' + (threadTitleWidth + 13) + 'px; top: 6px;">';
		var newTabID;
		
		if (index == currentThreadTab) {
			newTabID = "active-thread-title";
			newTabs = newTabs + '<div id="' + newTabID + '" class="thread-title" style="width: ' + threadTitleWidth + 'px;">' + escapeHTML(currentThreadTitle)
			                  + closeThreadTabButton
							  + '</div>'
		} else if (displayInactiveTabs) {
			newTabID = "thread-title-" + index;
			newTabs = newTabs + '<div id="' + newTabID + '" class="thread-title inactive" style="width: ' + threadTitleWidth + 'px;">' + escapeHTML(value.threadTitle)
			                  + closeThreadTabButton
							  + '</div>'
		}
		if (displayInactiveTabs)
			newTabs = newTabs + '<script>$("#" + "' + newTabID + '").draggable({'
							  + 'containment: "#thread-titles", '
							  + 'axis: "x",'
							  + 'start: function (event, ui) { threadTitlesStartDragging(event, ui, ' + index + ')},'
							  + 'stop: function (event, ui) { threadTitlesStopDragging(event, ui, ' + index + ')} '
							  + '}).'
							  + 'click(function (event, ui) { switchThreadTabs(' + index + '); });'
							  + '</script>';		

	});
	
	newTabs = newTabs + '<div id="add-thread-tab-button" onclick="addThreadTab();">+</div>';
	newTabs = newTabs + '<div style="clear: both;"></div>';
	
	$('#thread-titles').html(newTabs);
}

function addThreadTab () {
	if (threadTabs.length >= maxThreadTabCount)
		return;

	if (previousUpdateThreadContentRequest)
		previousUpdateThreadContentRequest.abort();

	saveCurrentThreadTab();
	
	currentThreadURL = "";
	currentThreadTitle = "スレッド";
	$('#thread-content').html("");
	threadHistory = [];
	threadHistoryForward = [];
	$('#thread-url-text-field').val("");
	$('#post-search-text-field').val("");
	imageList = [];
	unloadedThumbnailList = [];
	loadedThumbnailList = [];
	reverseAnchors = [];

	threadTabs.push({threadURL: currentThreadURL, threadTitle: currentThreadTitle, threadContent: null, position: 0, imageList: [], thumbnailList: []});
	currentThreadTab = threadTabs.length - 1;
	
	updateStarForFavoriteThread();
	adjustComponents();
}

function closeThreadTab(event, index) {
	event.stopPropagation();
	if (threadTabs.length <= 1) {
		currentThreadURL = "";
		currentThreadTitle = "スレッド";
		$('#thread-content').html("");
		threadHistory = [];
		threadHistoryForward = [];
		$('#thread-url-text-field').val("");
		$('#post-search-text-field').val("");
		imageList = [];
		unloadedThumbnailList = [];
		loadedThumbnailList = [];
		reverseAnchors = [];
		saveCurrentThreadTab();
	} else {
		saveCurrentThreadTab();
		threadTabs.splice(index, 1);
		if (currentThreadTab >= threadTabs.length)
			--currentThreadTab;
		loadCurrentThreadTab();
	}
	updateStarForFavoriteThread();
	adjustComponents();
	loadThreadContentIfEmpty();
}

function switchToRightThreadTab(threadTitle, threadURL) {
	var newTab = -1;
	
	// saveCurrentThreadTab();
	
	if (threadURL == null|| threadURL == "") {
		jQuery.each(threadTabs, function (index, value) {
			if (value.threadTitle == threadTitle)
				newTab = index;
		});
	} else {
		jQuery.each(threadTabs, function (index, value) {
			if (value.threadURL == threadURL)
				newTab = index;
		});
	}
	if (newTab >= 0) {
		switchThreadTabs(newTab);
		return;
	}
	
	if (threadTabs.length >= maxThreadTabCount || currentThreadTitle == "スレッド")
		return;

	// addThreadTab();
	return;
}

function openPostWindow(thread, message, threadUrl) {
	if (typeof message == 'undefined') message = "";
	if (typeof threadUrl == 'undefined') threadUrl = (currentThreadURL ? currentThreadURL : getFirstVisiblePostThreadUrl());;

	if (threadUrl && !thread) {
		var postWindow = open('/post?' + // Math.floor((Math.random() * 90000000) + 10000000) +
		                          '&thread-url=' + encodeURIComponent(threadUrl)
		     	                + '&new-thread-tab=' + (currentThreadURL ? '0' : '1')
								+ '&message=' + encodeURIComponent(message),
			                    randomElementID(),
		                        centerPopupWindow(500, 400) + ", resizable  = yes" + ", scrollbars = yes");
		postWindow.focus();
	} else if (currentBoardURL && thread) {
		var postWindow = open('/post?' + // Math.floor((Math.random() * 90000000) + 10000000) +
								 '&board-url=' + encodeURIComponent(currentBoardURL)
							   + '&message=' + encodeURIComponent(message),
								randomElementID(),
							   centerPopupWindow(500, 400) + ", resizable  = yes" + ", scrollbars = yes");
		postWindow.focus();
	}
}

function openOriginalThread() {
	var threadUrl = (currentThreadURL ? currentThreadURL : getFirstVisiblePostThreadUrl());
	if (threadUrl) {
		openURLInNewWindowWithReferrerDisabled(threadUrl);
	}
}

function deleteThreadLog() {
	var threadUrl = (currentThreadURL ? currentThreadURL : getFirstVisiblePostThreadUrl());
	if (threadUrl) {
		$.ajax({ 
			url: '/api-delete-thread-log?thread-url=' + encodeURIComponent(threadUrl),
			async: true, 
			success: function(result){
				updateStarForFavoriteThread();
			},
			error:  function(result, textStatus, errorThrown){
				updateStarForFavoriteThread();
			}
		});
	}
}

function openBoardForCurrentThread()
{
	var threadUrl = (currentThreadURL ? currentThreadURL : getFirstVisiblePostThreadUrl());

	if (threadUrl) {
		$.ajax({ 
			url: '/api-convert-thread-url-to-board-url?thread-url=' + encodeURIComponent(threadUrl),
			async: true, 
			success: function(result){
				if (result.length > 0)
					updateThreadList(null, result);
			}
		});
	}
}

function displayThreadMenu(event, threadURL) {
	var id = 'thread-menu';

	if ($('#' + id).length > 0 || !doesWindowHaveFocus)
		return;
	
	var menuDiv = createPopupMenu(event, id);

	menuDiv.append($("<div />")
	                   .attr("class", "popup-menu-item")
					   .attr("onmousedown", "updateThreadContent(null, '" + threadURL + "', '', '', false); $(event.target).parent().remove();")
					   .css('background-color', '#C7D7F1')
					   .html("開く"));
	
	menuDiv.append($("<div />")
	                   .attr("class", "popup-menu-item")
					   .attr("onmousedown", "updateThreadContent(null, '" + threadURL + "', '', '', true); $(event.target).parent().remove();")
					   .html("新しいタブで開く"));
				  
	$("#popup-menus").append(menuDiv);
	$('.popup-menu-item').hover(function(event) { $(this).css('background-color', event.type === 'mouseenter' ? '#C7D7F1' : 'transparent'); });
	
	keepElementWithinWindow('#' + id);
}




////////////////////
// FLOATING POSTS //
////////////////////

var delayForFloatingPost = 300;

function addFloatingPost(event, id, content) {
	var div = $("<div />");

	div.attr("id", id);
	div.attr("class", "floating-post");
	div.attr("onmouseleave", "removeFloatingPosts(event);");
	div.css("top", currentMouseY - 12);
	div.css("left", currentMouseX - 64);
	div.html(content);
	div.css({'opacity': 0});

	$("#floating-posts").append(div);
	
	keepElementWithinWindow('#' + id);
}

function displayFloatingPost(event, service, board, threadNo, postIndexes, postCount) {
	var id = 'floating-post-' + postIndexes.replace(/,/g, '_');
	var parsedIndexes = [];
	var posts = "";
	
	// console.log("displayFloatingPost(" + event + ", " + postIndexes + ", " + postCount + ")");
	
	setTimeout(function () {
		// console.log("$('#' + id).length: " + $('#' + id).length);
		// console.log("doesWindowHaveFocus: " + doesWindowHaveFocus);
		
		if ($('#' + id).length > 0 || !doesWindowHaveFocus)
			return;
		
		// console.log("isMouseCursorOnElement(event.target): " + isMouseCursorOnElement(event.target));
		
		if (!isMouseCursorOnElement(event.target))
			return;
		
		postIndexes.split(",").forEach(function (entry) {
			// console.log('entry: ' + entry);
			if (entry.match(/^[0-9]+-[0-9]+$/)) {
				range = entry.split(/-/);
				for (var j = parseInt(range[0]); 
					 j <= parseInt(range[1]); 
					 ++j)
					 parsedIndexes.push(j);
			} else {
				parsedIndexes.push(parseInt(entry));
			}
		});
		
		parsedIndexes.forEach(function (entry) {
			if (   entry <= postCount
				&& $(createSelectorForPost(service, board, threadNo, entry, 'heading')).length > 0
				&& $(createSelectorForPost(service, board, threadNo, entry, 'message')).length > 0
				&& !($(createSelectorForPost(service, board, threadNo, entry, 'heading')).hasClass("aborn"))
				&& !($(createSelectorForPost(service, board, threadNo, entry, 'message')).hasClass("aborn"))) {
				posts += '<div class="floating-post-heading">' + $(createSelectorForPost(service, board, threadNo, entry, 'heading')).html() + '</div>';
				posts += '<div class="floating-post-message">' + $(createSelectorForPost(service, board, threadNo, entry, 'message')).html() + '</div>';
			}
		});
		if (posts.length <= 0)
			posts = '<div class="floating-post-message">' + 'あぼ～ん' + '</div>';
		
		addFloatingPost(event, id, posts);
	}, delayForFloatingPost);
}

function displayFloatingPostWithID(event, service, board, threadNo, postID) {
	var id = 'floating-post-' + postID;
	var posts = "";
	
	setTimeout(function () {
		if ($('#' + id).length > 0)
			return;
		
		if (!isMouseCursorOnElement(event.target))
			return;
		
		$('.' + postID).each(function () {
			if ($(this).hasClass("aborn"))
				return;
			if ($(this).hasClass('post-heading')) {
				posts += '<div class="floating-post-heading">' + $(this).html() + '</div>'
			} else {
				posts += '<div class="floating-post-message">' + $(this).html() + '</div>'
			}
		});
		if (posts.length <= 0)
			posts = '<div class="floating-post-heading">' + 'あぼ～ん' + '</div>';
		
		addFloatingPost(event, id, posts);
	}, delayForFloatingPost);
}

function removeFloatingPosts(event) {
	var lastItemToKeep = -1;
	var index = 0;
	$('.floating-post').each(function () {
		if (  $(this).offset().left <= event.clientX && event.clientX < $(this).offset().left + $(this).width()
		    && $(this).offset().top  <= event.clientY && event.clientY < $(this).offset().top + $(this).height()) {
			lastItemToKeep = index;
		}
		++index;
	});
	index = 0;
	$('.floating-post').each(function () {
		if (index > lastItemToKeep)
			$(this).remove();
		++index;
	});
}

function removeAllFloatingPosts() {
	$('#floating-posts').html("");
}

function updateIDs() {
	var IDs = [];
	var IDCounts = {};

	$('.id-in-heading').each(function () {
		var rawID = 'ID:' + $(this).text();
		IDs.push(rawID);
		if (IDCounts[rawID] > 0) {
			++IDCounts[rawID];
		} else {
			IDCounts[rawID] = 1;
		}
	});
	
	var uniqueIDs = IDs.filter(function (value, index, self) {
	    return self.indexOf(value) === index;
	});
	
	jQuery.each(uniqueIDs, function (index, rawID) {
		var convertedID = rawID.replace(/^ID:/, 'id-').replace(/[\+.]/g, '-').replace(/\//g, '_');
		if (IDCounts[rawID] > 1) {
			if (MSIEVersion() <= 0) {
				$('.id-in-heading-' + convertedID).each(function () {
					$(this).css('color', (IDCounts[rawID] <= 3) ? 'blue' : 'red');
					$(this).addClass('with-count');
				});
				$('.id-in-heading-' + convertedID + '-count').each(function () {
					$(this).css('display', 'inline-block');
				});
				$('.id-in-message-' + convertedID).each(function () {
					$(this).css('color', (IDCounts[rawID] <= 3) ? 'blue' : 'red');
					$(this).addClass('with-count');
				});
				$('.id-in-message-' + convertedID + '-count').each(function () {
					$(this).css('display', 'inline-block');
				});
			}
			$('.id-in-heading-' + convertedID + '-count').each(function () {
				$(this).css('color', (IDCounts[rawID] <= 3) ? 'blue' : 'red');
				$(this).html(IDCounts[rawID]);
			});
			$('.id-in-message-' + convertedID + '-count').each(function () {
				$(this).css('color', (IDCounts[rawID] <= 3) ? 'blue' : 'red');
				$(this).html(IDCounts[rawID]);
			});
		}
	});
}

var reverseAnchors = [];

function addReverseAnchors(to, from, postCount) {
	// console.log(to + ', ' + from + ', ' + postCount);
	// return;

	var parsedIndexes = [];
	
	if (to > postCount || MSIEVersion() > 0)
		return;
	
	from.split(",").forEach(function (entry) {
		if (entry.match(/^[0-9]+-[0-9]+$/)) {
			range = entry.split(/-/);
			for (var j = parseInt(range[0]); 
				 j <= parseInt(range[1]); 
				 ++j)
				 parsedIndexes.push(j);
		} else {
			parsedIndexes.push(parseInt(entry));
		}
	});
	
	parsedIndexes.forEach(function (entry) {
		if (entry <= postCount)
			reverseAnchors.push({from: entry, to: to});
	});
}

function createReverseAnchors(service, board, threadNo)
{
	if (MSIEVersion() > 0)
		return;

	var grouped = [];
	
	reverseAnchors.forEach(function (entry) {
		if (!grouped[entry.from])
			grouped[entry.from] = [];
		grouped[entry.from].push(entry.to);
	});
	
	grouped.forEach(function (entry, from) {
		var anchors = "", lastAnchor;
		
		entry
		.filter(function(itm,i,a){ return i==a.indexOf(itm); })
		.sort(function(a, b) { return a - b; })
		.forEach(function (to) {
			if ($(createSelectorForPost(service, board, threadNo, to, 'message')).length > 0)
				anchors = anchors + to + ",";
			lastAnchor = to;
		});
		
		anchors = anchors.replace(/,$/, "");
		if (anchors != "") {
			var elementID = randomElementID();
 			$(createSelectorForPost(service, board, threadNo, from, 'message'))
			    .append($("<div />")
				            .attr("class", "reverse-anchors-container")
				            .append($("<div />")
			                    .attr("id", elementID)
			                    .attr("class", "reverse-anchors")
			                    .attr("onmouseover", "displayFloatingPost(event, '" + service + "', '" + board + "', '" + threadNo + "', '" + anchors + "', " + lastAnchor + ");")
			                    .html(anchors.replace(/,/g, ', '))));
		}
	});
}

var updateBookmarkRequest = null;
var updateBookmarkRequestTime = 0;

function updateBookmark(service, board, threadNo, postIndex) {
	if (updateBookmarkRequest && Date.now() - updateBookmarkRequestTime < 10000)
		return
	if (this.prevService == service && this.prevBoard == board && this.prevThreadNo == threadNo && this.prevPostIndex == postIndex && this.prevCount > 0)
		return;
	
	var postHeadingSelector = '#' + createElementIdForPost(service, board, threadNo, (postIndex + 1).toString(), 'heading');
	while ($(postHeadingSelector).length > 0 && ($(postHeadingSelector).hasClass('aborn') || $(postHeadingSelector).hasClass('hidden'))) {
		++postIndex;
		postHeadingSelector = '#' + createElementIdForPost(service, board, threadNo, (postIndex + 1).toString(), 'heading');
	}

    console.log('updateBookmark: ' + service + ', ' + board + ', ' + threadNo + ', ' + postIndex);
	
	if (this.prevService == service && this.prevBoard == board && this.prevThreadNo == threadNo && this.prevPostIndex == postIndex) {
		this.prevCount += 1;
	} else {
		this.prevService = service; 
		this.prevBoard = board;
		this.prevThreadNo = threadNo;
		this.prevPostIndex = postIndex;
		this.prevCount = 0;
	}
	
	updateBookmarkRequestTime = Date.now();
	updateBookmarkRequest = $.ajax({ 
		url:   '/api-update-bookmark'
		     + '?service=' + service
		     + '&board=' + board 
		     + '&thread-no=' + threadNo
		     + '&post-index=' + postIndex,
		async: true,
		success: function(result){
			var newPostCountId = ('#new-post-count-' + service + '-' + board + '-' + threadNo).replace(/[.\/]/g, '_');
			var newPostCount = (result.split(','))[0];
			var prevNewPostCount = $(newPostCountId).text();
			$(newPostCountId).html(newPostCount);
			if (parseInt(newPostCount)) {
				$(newPostCountId).addClass('non-zero');
			} else {
				$(newPostCountId).removeClass('non-zero');
			}

			var postCountId = ('#post-count-' + service + '-' + board + '-' + threadNo).replace(/[.\/]/g, '_');
			var postCount = (result.split(','))[1];
			var prevPostCount = $(postCountId).text();
			$(postCountId).html(postCount);
			if (prevPostCount != postCount || prevNewPostCount != newPostCount)
				$('#thread-list-table').trigger('update');

			var newPostCountForBoardSelector = '.' + ('new-post-count-' + service + '-' + board).replace(/[.\/]/g, '_');
			var viewedPostCount = parseInt((result.split(','))[2]);
			if (viewedPostCount > 0 && previousLoadFavoriteBoardListRequest)
				previousLoadFavoriteBoardListRequest.abort();
			// console.log('$(newPostCountForBoardSelector).length: ' + $(newPostCountForBoardSelector).length);
			// console.log('viewedPostCount: '+ viewedPostCount);
			$(newPostCountForBoardSelector).each(function(index, element) {
				var prevNewPostCountForBoard = parseInt($(element).text());
				var newPostCountForBoard = (prevNewPostCountForBoard > viewedPostCount) ? (prevNewPostCountForBoard - viewedPostCount) : 0;
				
				$(element).html(newPostCountForBoard);
				if (newPostCountForBoard) {
					$(element).addClass('non-zero');
				} else {
					$(element).removeClass('non-zero');
					$('.' + ('board-name-' + service + '-' + board).replace(/[.\/]/g, '_')).css('font-weight', 'normal'); // not the best solution
				}
			});
		},
		error:  function(result, textStatus, errorThrown){
		},
		complete: function(result, textStatus) {
			updateBookmarkRequest = null;
		}
	});
}

updateBookmark.prevService   = null;
updateBookmark.prevBoard     = null;
updateBookmark.prevThreadNo  = null;
updateBookmark.prevPostIndex = null;
updateBookmark.prevCount     = 0;

function checkForReadPosts() {
	var threadContentPosts = $('div#thread-content-wrapper > div.post-message:not(.aborn):not(.hidden)');
	
	if (threadContentPosts.length <= 0)
		return;
	
	var prevService   = null;
	var prevBoard     = null;
	var prevThreadNo  = null;
	var prevPostIndex = null;
	var lowerBound = $('#thread-content-wrapper').scrollTop() + $('#thread-content').offset().top;
	var upperBound = $('#thread-content').height() + $('#thread-content').offset().top;
	
	// console.log('threadContentPosts.length: ' + threadContentPosts.length);
	
	var lower = 0, upper = threadContentPosts.length - 1;
	while (lower < upper) {
		var middle = Math.floor((lower + upper) / 2);
		
		// console.log(($(threadContentPosts[middle]).height() == threadContentPostsHeight[middle]) ? "OK" : "NG");
		if (lowerBound <= $(threadContentPosts[middle]).offset().top + $(threadContentPosts[middle]).height()) {
			upper = middle - 1;
		} else {
			lower = middle + 1;
		}
	}

	// console.log('lower: ' + lower);
	
	var parentScrollTop = $(threadContentPosts[0]).parent().scrollTop();
	for (var index = lower; index < threadContentPosts.length; ++index) {
		var element = threadContentPosts[index];
		var offsetTop =  $(element).offset().top;
		var height    = $(threadContentPosts[index]).height();
		
	    if (lowerBound <= offsetTop - parentScrollTop + height && offsetTop - parentScrollTop + height * 0.8 <= upperBound) {
			var service   = $(element).attr('post-service');
			var board     = $(element).attr('post-board');
			var threadNo  = $(element).attr('post-thread-no');
			var postIndex = parseInt($(element).attr('post-index'));
			
			if (prevService && (prevService != service || prevBoard != board || prevThreadNo != threadNo)) {
				updateBookmark(prevService, prevBoard, prevThreadNo, prevPostIndex)
				prevService   = null;
				prevBoard     = null;
				prevThreadNo  = null;
				prevPostIndex = null;
			}
			prevPostIndex = (prevService == service && prevBoard == board && prevThreadNo == threadNo && prevPostIndex > postIndex) ? prevPostIndex : postIndex;
			prevService = service;
			prevBoard = board;
			prevThreadNo = threadNo;
		}
		if (upperBound < offsetTop - parentScrollTop)
			break;
    }
	if (prevService)
		updateBookmark(prevService, prevBoard, prevThreadNo, prevPostIndex);
}

$(document).ready(function() { setInterval(checkForReadPosts, 500); });

function updateFixedThreadHeading() {
	var headings = $('div#thread-content-wrapper > div.thread-heading');
	
	if (headings.length == 1) {
		$('#fixed-thread-heading').html($(headings).html());
	} else {
		var lowerBound = $('#thread-content-wrapper').scrollTop() + $('#thread-content').offset().top;
		var upperBound = $('#thread-content').height() + $('#thread-content').offset().top;
		headings.each(function(index, element) {
			if ($(element).offset().top  < lowerBound + $(element).height())
				$('#fixed-thread-heading').html($(element).html());
		});
	}
}

$(document).ready(function() { setInterval(updateFixedThreadHeading, 100); });

function getFirstVisiblePostThreadUrl() {
	var threadContentPosts = $('div#thread-content-wrapper > div.post-message:not(.aborn):not(.hidden)');
	var lowerBound = $('#thread-content-wrapper').scrollTop() + $('#thread-content').offset().top;
	var upperBound = $('#thread-content').height() + $('#thread-content').offset().top;
	var threadUrl = null;
	
	threadContentPosts.each(function(index, element) {
	    if (   lowerBound <= $(element).offset().top + $(element).height()
			&& $(element).offset().top - $(element).parent().scrollTop() + $(element).height() * 0.8 <= upperBound) {
			threadUrl  = $(element).attr('post-thread-url');
			return false;
		}
    });
	return threadUrl;
}



////////////////
// POST MENUS //
////////////////

var delayForPostMenu = 300;

function escapeString(s) {
	return s.replace(/[\\"']/g, '\\$&').replace(/\u0000/g, '\\0').replace(/\n/g, '\\n');
}
 	
function addAbornFilterWithPostSignature(service, board, threadNo, postIndex, postSignature) {
	// alert(postSignature);
	
	if ($(createSelectorForPost(service, board, threadNo, postIndex, 'heading')).length > 0)
		$(createSelectorForPost(service, board, threadNo, postIndex, 'heading')).addClass("aborn");
	if ($(createSelectorForPost(service, board, threadNo, postIndex, 'message')).length > 0)
		$(createSelectorForPost(service, board, threadNo, postIndex, 'message')).addClass("aborn");
		
	$.ajax({ 
		url: '/api-add-aborn-filter-with-post-signature?post-signature=' + encodeURIComponent(postSignature),
		async: true,
		success: function(result){ 
		},
		error:  function(result, textStatus, errorThrown){
		},
		complete: function(result, textStatus) {
		}
	});
}
 	
function addAbornFilterWithID(service, board, threadNo, postIndex, postID) {
	var convertedID = postID.replace(/^ID:/, 'id-').replace(/[\+.]/g, '-').replace(/\//g, '_');
		
	// alert(postID);
	
	$('.' + convertedID).each(function (index, element) {	$(this).addClass("aborn"); });
		
	$.ajax({ 
		url: '/api-add-aborn-filter-with-id?id=' + encodeURIComponent(postID),
		async: true,
		success: function(result){ 
		},
		error:  function(result, textStatus, errorThrown){
		},
		complete: function(result, textStatus) {
		}
	});
}

function displayPostMenu(event, server, service, board, threadNo, postIndex) {
	var id = 'popup-menu-' + postIndex;
	var postInHtml = "";
	var postInPlainText = "";
	var postSignature = "";
	var postID = "";
	var threadUrl = "";

	// console.log("displayPostMenu()");
	
	if (   $(createSelectorForPost(service, board, threadNo, postIndex, 'heading')).length > 0
	    && $(createSelectorForPost(service, board, threadNo, postIndex, 'message')).length > 0) {
		postInHtml += '<div class="floating-post-message">' + $(createSelectorForPost(service, board, threadNo, postIndex, 'message')).html().replace(/<script>[^>]+<\/script>/g, "") + '</div>';
		postInHtml = postInHtml
		                 .replace(/<br><div class="reverse-anchors"[^>]+>[^>]+<\/div>/, "")
						 .replace(/<!-- image-url: ([^>]+) -->/g, "$1")
						 .replace(/<!-- br -->/g, "<br>");
		postInPlainText = "> " + $(postInHtml.replace(/<br>/g, "\n")).text().replace(/\n/g, '\n> ');
		postInHtml = '<div class="floating-post-heading">' + $(createSelectorForPost(service, board, threadNo, postIndex, 'heading')).html().replace(/<span class="id-count[^>]+<\/span>/, "") + '\n</div>' + postInHtml;
		
		postSignature = $(createSelectorForPost(service, board, threadNo, postIndex, 'heading')).attr('post-signature');
		threadUrl = $(createSelectorForPost(service, board, threadNo, postIndex, 'message')).attr('post-thread-url');
		if (typeof $(createSelectorForPost(service, board, threadNo, postIndex, 'heading')).attr('post-id') != 'undefined')
			postID = $(createSelectorForPost(service, board, threadNo, postIndex, 'heading')).attr('post-id');
	}
		
	setTimeout(function () {
		// console.log("$('#' + id).length: " + $('#' + id).length);
		// console.log("doesWindowHaveFocus: " + doesWindowHaveFocus);
		
		if ($('#' + id).length > 0 || !doesWindowHaveFocus)
			return;
		
		// console.log("isMouseCursorOnElement(event.target): " + isMouseCursorOnElement(event.target));
		
		if (!isMouseCursorOnElement(event.target))
			return;
		
		var menuDiv = createPopupMenu(event, id);
	
		var itemDiv;
		itemDiv = $("<div />");
		itemDiv.attr("class", "popup-menu-item");
		itemDiv.click(function() {
			openPostWindow(false, (postInPlainText.length <= 0 ? "" : (">>" + postIndex + "\n")), threadUrl);
			$('#' + id).remove();
		});
		itemDiv.css('background-color', '#C7D7F1'); // not a clean solution, but it works...
		itemDiv.html("返信する");
		menuDiv.append(itemDiv);
		
		itemDiv = $("<div />");
		itemDiv.attr("class", "popup-menu-item");
		itemDiv.click(function() {
			openPostWindow(false, (postInPlainText.length <= 0 ? "" : (">>" + postIndex + "\n" + postInPlainText + "\n\n")), threadUrl);
			$('#' + id).remove();
		});
		itemDiv.html("引用して返信する");
		menuDiv.append(itemDiv);
		
		itemDiv = $("<div />");
		itemDiv.attr("class", "popup-menu-item");
		itemDiv.attr("onmousedown", 'addAbornFilterWithPostSignature("' + service + '", "' + board + '", "' + threadNo + '", ' + postIndex + ', decodeURIComponent("' + encodeURIComponent(postSignature) + '")); $("#' + id + '").remove();');
		itemDiv.html("あぼ～んする");
		menuDiv.append(itemDiv);
		
		if (postID.length > 0) {
			itemDiv = $("<div />");
			itemDiv.attr("class", "popup-menu-item");
			itemDiv.attr("onmousedown", 'addAbornFilterWithID("' + service + '", "' + board + '", "' + threadNo + '", ' + postIndex + ', decodeURIComponent("' + encodeURIComponent(postID) + '")); $("#' + id + '").remove();');
			itemDiv.html("IDであぼ～んする");
			menuDiv.append(itemDiv);
		}
		
		/*
		   "<div class= onclick=\"></div>"
		              + "<div class=popup-menu-item onclick=\"\"></div>"
		              + "<div class=popup-menu-item>あぼ～んする</div>"
		              + "<div class=popup-menu-item>ここまで読んだ</div>";
		*/
					  
		$("#popup-menus").append(menuDiv);
		$('.popup-menu-item').hover(function(event) { $(this).css('background-color', event.type === 'mouseenter' ? '#C7D7F1' : 'transparent'); });
		
		keepElementWithinWindow('#' + id);
	}, delayForPostMenu);
}

function removeAllPopupMenus() {
	$('#popup-menus').html("");
}



///////////////
// TEXTBOXES //
///////////////

$(document).ready(function(){
	$('#thread-url-text-field').keyup(function(ev) {
		if(ev.keyCode == 13 && $('#thread-url-text-field').val().length > 0) {
			updateThreadContent(null, $('#thread-url-text-field').val());
			$('#thread-url-text-field').blur();
		}
	});
	
	$('#thread-search-text-field').keypress(function(ev) {
		if(ev.keyCode == 13 && currentBoardURL /*&& $('#thread-search-text-field').val().length > 0*/) {
			updateThreadList(currentBoardName, currentBoardURL, $('#thread-search-text-field').val(), "", false, false, logListMode);
			$('#thread-search-text-field').blur();
		} else if (ev.keyCode == 13 && currentBoardName == 'お気にスレ') {
			loadFavoriteThreadList(false, $('#thread-search-text-field').val(), "");
			$('#thread-search-text-field').blur();
		} else if (ev.keyCode == 13 && currentBoardName == '最近読んだスレ') {
			loadRecentlyViewedThreadList(false, $('#thread-search-text-field').val(), "");
			$('#thread-search-text-field').blur();
		} else if (ev.keyCode == 13 && currentBoardName == '書込履歴') {
			loadRecentlyPostedThreadList(false, $('#thread-search-text-field').val(), "");
			$('#thread-search-text-field').blur();
		} else if (ev.keyCode == 13 && currentBoardName == 'DATファイル一覧') {
			loadDatFileList(false, $('#thread-search-text-field').val(), "");
			$('#thread-search-text-field').blur();
		} else if (ev.keyCode == 13 && currentBoardName == 'HTMLファイル一覧') {
			loadHtmlFileList(false, $('#thread-search-text-field').val(), "");
			$('#thread-search-text-field').blur();
		}
	});
	
	$('#board-url-text-field').keypress(function(ev) {
		if(ev.keyCode == 13 && $('#board-url-text-field').val().length > 0) {
			updateThreadList(null, $('#board-url-text-field').val());
			$('#board-url-text-field').blur();
		}
	});
	
	$('#post-search-text-field').keypress(function(ev) {
		if(ev.keyCode == 13 && currentThreadURL /*&& $('#thread-search-text-field').val().length > 0*/) {
			updateThreadContent(currentThreadTitle, currentThreadURL, $('#post-search-text-field').val(), "");
			$('#post-search-text-field').blur();
		}
	});
});



////////////
// IMAGES //
////////////

function resetThumbnailLists() {
	unloadedThumbnailList = [];
	loadedThumbnailList = [];
}

function addThumbnail(id, src) {
	// console.log("addThumbnail(" + id + ", " + src + ");");
	if (src)
		unloadedThumbnailList.push({id: id, src: src});
}

function resizeThumbnail(thumbnailID) {
	// console.log('resizeThumbnail(): ' + thumbnailID);
	if ($('#' + thumbnailID).length <= 0)
		return; 
	
	var height = $('#' + thumbnailID).parent().height(); 
	var width  = $('#' + thumbnailID).parent().width();
	var left = 0;
	var top = 0;
	var newHeight = height;
	var newWidth = width;
	var isLoaded = false;
	
	try {
		isLoaded =    document.getElementById(thumbnailID).complete
		           && document.getElementById(thumbnailID).naturalWidth
		           && document.getElementById(thumbnailID).naturalHeight;
	} catch (e) {
	}
	// console.log('isLoaded: ' + isLoaded);

	if (!isLoaded) {
		setTimeout(function() { resizeThumbnail(thumbnailID); }, 100);
		return;
	}

	// Adjust the width and height of the thumbnail.
	var naturalWidth  = width;
	var naturalHeight = height;
	try {
		naturalWidth  = document.getElementById(thumbnailID).naturalWidth;
		naturalHeight = document.getElementById(thumbnailID).naturalHeight;
	} catch (e) {
	}
	// assumes height == width
	if (naturalWidth > naturalHeight) {
		newHeight = width * (naturalHeight / naturalWidth);
		top = Math.floor((height - newHeight) * 0.5) + 'px';
	} else if (naturalWidth < naturalHeight) {
		newWidth = height * (naturalWidth / naturalHeight);
		left = Math.floor((width - newWidth) * 0.5) + 'px';
	}

	// markImageAsAvailable($('#' + thumbnailID).attr('src')); // The argument should be the actual source, but thumbnail.src works if the image is not cached.
	$('#' + thumbnailID).parent().css('background', 'black');

	$('#' + thumbnailID)
		.width(newWidth)
		.height(newHeight)
		.attr('width', newWidth)
		.attr('height', newHeight)
		.css('left', left)
		.css('top', top);
}

function loadThumbnail(thumbnailID, thumbnailSource, loadAnotherThumbnail)
{
	// console.log("loadThumnbnail(): '" + thumbnailID + "', '" + thumbnailSource + "', '" + loadAnotherThumbnail + "'");
	$('#' + thumbnailID)
		// .width(64)
		// .height(64)
		// .attr('width', 64)
		// .attr('height', 64)
		.css('left', 0)
		.css('top', 0)
		.css('width',  $('#' + thumbnailID).parent().css('width'))
		.css('height', $('#' + thumbnailID).parent().css('height'))
		.attr('src', '/img/thumbnail-spinner.gif');
		
	setTimeout(function() {
		var cachedSource = thumbnailSource + (loadAnotherThumbnail ? "" : ('?' + new Date().getTime()));
		$.each(imageList, function (index, element) {
			if (element.src == thumbnailSource) {
				element.cachedSource = cachedSource;
				return false;
			}
			return true;
		});
		
		$('#' + thumbnailID).attr('src', cachedSource);
		if (!isBrowserFirefox()) {
			// Didn't work with FF 32.0.3
			$('#' + thumbnailID).imagesLoaded().progress(function (instance, image) {
				// console.log("imagesLoaded().progress(): " + thumbnailID);
				if (!image.isLoaded)
					$('#' + thumbnailID).attr('src', imageThumbnailFailedSource);
				setTimeout(function() { resizeThumbnail(thumbnailID); }, 100);
				if (loadAnotherThumbnail)
					loadOneThumbnail();
			});
		} else {
			$('#' + thumbnailID).bind('load', function() {
				setTimeout(function() { resizeThumbnail(thumbnailID); }, 100);
				if (loadAnotherThumbnail)
					loadOneThumbnail();
			});
			$('#' + thumbnailID).bind('error', function() {
				$('#' + thumbnailID).attr('src', imageThumbnailFailedSource);
				setTimeout(function() { resizeThumbnail(thumbnailID); }, 100);
				if (loadAnotherThumbnail)
					loadOneThumbnail();
			});
		}
	}, 0);
}

function loadOneThumbnail()
{
	var thumbnail = unloadedThumbnailList.shift();
	if (!thumbnail)
		return;
	loadedThumbnailList.push(thumbnail);
	loadThumbnail(thumbnail.id, thumbnail.src, true);
}

function loadThumbnails() {
	var thumbnail;
	var count = maximumNumberOfImageDownloads;
	
	while (count-- > 0 && unloadedThumbnailList.length > 0)
		loadOneThumbnail();
}

function stopCurrentDownloads() {
	$.ajax({ 
		url: '/api-stop-current-downloads',
		async: true,
		success: function(result){ 
			updateDownloadStatus();
		},
		error:  function(result, textStatus, errorThrown){
		}
	});
}

function setImageViewerSize() {
	if ($('#image-viewer-image').length <= 0 || $('#image-viewer-background').length <= 0)
		return;
	
	var width = document.getElementById('image-viewer-image').naturalWidth;
	var height = document.getElementById('image-viewer-image').naturalHeight;
	
	if (!width || !height) {
		setTimeout(function (instance) { setImageViewerSize(); }, 100);
		return;
	}
	
	if (width > $(window).width()) {
		height *= $(window).width() / width;
		width = $(window).width();
	}
	
	if (height > $(window).height()) {
		width *= $(window).height() / height;
		height = $(window).height();
	}
	
	$('#image-viewer-background')
	    .html('')
		.css('width', $(window).width() + 'px')     
		.css('height', $(window).height() + 'px');
	$('#image-viewer-image')
		.css('left', ($(window).width() - width) / 2)       
		.css('top', ($(window).height() - height) / 2)
		.css('width', width + 'px')     
		.css('height', height + 'px')
//		.css('opacity', '1');
		.animate({opacity: 1}, {duration: animationDurationForImageViewer});
}
	
var imageViewerCurrentImage = {
	service: null,
	board: null,
	threadNo: null,
	postIndex: 1
};

var imageViewerPostIndex = 1; // dummy

function openImageViewer(src, service, board, threadNo, postIndex) {
	var cachedSource = src;
	var thumbnailID = null;
	var url = null;
	var md5String = null;
	
	$.each(imageList, function (index, element) {
        if (element.src == src && element.postIndex == postIndex) {
			if (element.cachedSource)
				cachedSource = element.cachedSource;
			thumbnailID = element.thumbnailID;
			url = element.url;
			md5String = element.md5String;
			return false;
		}
		return true;
    });
	
	var background = $('<div />')
	                      .attr('id', 'image-viewer-background')
	                      .css('background', 'black')       
						  .css('opacity', '0.7')
						  .css('position', 'absolute')       
						  .css('left', 0)       
						  .css('top', 0)         
						  .css('width', $(window).width() + 'px')       
						  .css('height', $(window).height() + 'px')
						  .mousedown(function(event) {
							  event.preventDefault();
							  if (event.which == 3) {
								  displayImageViewerMenu(event, src, url, thumbnailID, md5String, postIndex);
							  } else {
								  closeImageViewer(event);
							  }
                          })
						  .css('opacity', '0')
						  .animate({opacity: 0.7}, {duration: animationDurationForImageViewer})
						  .attr('oncontextmenu', 'return false;')
						  .mousewheel(function(event) {
    						  if (event.deltaY < 0) {
                        		  displayNextImage(false);
							  } else if (event.deltaY > 0) {
                        		  displayPreviousImage(false);
							  }
    					  });

	var image = $('<img />')
	                .attr('id', 'image-viewer-image')
	                .attr('tabindex', '1')
					.css('position', 'absolute')       
					  .css('opacity', '0')
					.mousedown(function(event) {
						event.preventDefault();
						if (event.which == 2 && src.match(/^\//)) { 
							open(src, '_blank'); 
						} else if (event.which == 2) {
							openURLInNewWindowWithReferrerDisabled(src);
						} else if (event.which == 3) {
							displayImageViewerMenu(event, src, url, thumbnailID, md5String, postIndex);
						} else {
	                        displayNextImage(true);
						}
                    })
				    .attr('oncontextmenu', 'return false;')
				    .mousewheel(function(event) {
					    if (event.deltaY < 0) {
						    displayNextImage(false);
					    } else if (event.deltaY > 0) {
						    displayPreviousImage(false);
					    }
				    })
					.attr('src', cachedSource);
	if ($('#image-viewer-background').length <= 0) {
		$('#image-viewer').css('opacity', '1').append(background.html(ajaxSpinnerBarsWhite)).append(image);
	} else {
		$('#image-viewer-background').html(ajaxSpinnerBarsWhite);
		$('#image-viewer').css('opacity', '1').append(image);
	}
	setTimeout(function() {
		$('#image-viewer-image')
			.focus()
			.keydown(function(event) {
				event.preventDefault();
				event.stopPropagation();
				if((event.keyCode == 32 && !event.shiftKey) || event.keyCode == 13) {
					displayNextImage(true);
				} else if (event.keyCode == 39 || event.keyCode == 40) {
					displayNextImage(false);
				} else if ((event.keyCode == 32 && event.shiftKey) || event.keyCode == 8 || event.keyCode == 37 || event.keyCode == 38) {
					displayPreviousImage(false);
				} else if (event.keyCode == 27) {
					closeImageViewer();
				}
			});
	}, 0);
	
	imageViewerCurrentImage = {
		service: service, 
		board: board, 
		threadNo: threadNo,
		postIndex: postIndex
	}
	setTimeout(function (instance) { setImageViewerSize(); }, 100);
}

function actuallyCloseImageViewer() {
	$('#image-viewer').html('');
	removeFloatingPosts(event);
}
	
function closeImageViewer(event) {
	$('#image-viewer').animate({opacity: 0}, {duration: animationDurationForImageViewer /*, complete: actuallyCloseImageViewer */});
	setTimeout(actuallyCloseImageViewer, animationDurationForImageViewer); // Sometimes complete does not fire.
}

function addImage(src, service, board, threadNo, postIndex, cached, thumbnailID, url, md5String) {
	var found = false;
	$.each(imageList, function (index, element) {
        if (element.src == src && element.postIndex == postIndex) {
			found = true;
			return false;
		}
		return true;
    });
	if (!found) {
		imageList.push({
			index: imageList.length, 
			src: src, 
			service: service,
			board: board, 
			threadNo: threadNo, 
			postIndex: postIndex, 
			available: cached, 
			thumbnailID: thumbnailID, 
			cachedSource: null,
			url: url, 
			md5String: md5String
			});
		// imageList.sort(function(a, b) { return a.postIndex - b.postIndex; });
	}
}

/*
function markImageAsAvailable(src) {
	console.log('markImageAsAvailable(' + src + ')');
	imageList.forEach(function (element) {
        if (element.src == src) {
			element.available = true;
			return false;
		}
    });
}
*/

function compareImages(a, b) {
	return (a.server != b.server)       ? a.server.localeCompare(b.server) :
			(a.service != b.service)     ? a.service.localeCompare(b.service) :
			(a.threadNo != b.threadNo)   ? a.threadNo.localeCompare(b.threadNo) :
			(a.postIndex != b.postIndex) ? (a.postIndex - b.postIndex) :
			(a.index - b.index);
}

function compareImagePosts(a, b) {
	return (a.server != b.server)       ? a.server.localeCompare(b.server) :
			(a.service != b.service)     ? a.service.localeCompare(b.service) :
			(a.threadNo != b.threadNo)   ? a.threadNo.localeCompare(b.threadNo) :
			(a.postIndex - b.postIndex);
}

function updateImageList() {
	imageList.sort(compareImages);
	$.each(imageList, function (index, element) {
		if (element.thumbnailID) {
			element.available = false;
			try {
				if (   document.getElementById(element.thumbnailID).complete
				    && $('#' + element.thumbnailID).attr('src') != '/img/thumbnail-ng.png'
				    && $('#' + element.thumbnailID).attr('src') != '/img/thumbnail-failed.png'
				    && $('#' + element.thumbnailID).attr('src') != '/img/thumbnail-download-failed.png'
					&& document.getElementById(element.thumbnailID).naturalWidth
					&& document.getElementById(element.thumbnailID).naturalHeight)
				element.available = true;
			} catch (e) {
			}
		}
	});
}

function displayNextImage(closeIfNotFound) {
	console.log('displayNextImage()');
	var found = false;
	var opened = false;
	
	if ($('#image-viewer-image').length <= 0 || $('#image-viewer-background').length <= 0) {
        $('#image-viewer').html('');
		return;
	}
	updateImageList();
	$.each(imageList, function (index, element) {
		if (compareImagePosts(element, imageViewerCurrentImage) == 0) {
			console.log('element.src:                          ' + element.src);
			console.log('element.cachedSource:                 ' + element.cachedSource);
			console.log("$('#image-viewer-image').attr('src'): " + $('#image-viewer-image').attr('src'));
		}
        if (   compareImagePosts(element, imageViewerCurrentImage) == 0
		    && (   element.src == $('#image-viewer-image').attr('src').replace(/\?[0-9]+$/, '')
		        || element.cachedSource == $('#image-viewer-image').attr('src'))) {
			found = true;
		} else if (found && compareImagePosts(element, imageViewerCurrentImage) == 0 && element.available) {
	        $('#image-viewer-image').animate({opacity: 0}, {duration: animationDurationForImageViewer, complete: function() {
				$('#image-viewer-image').remove();
				openImageViewer(element.src, element.service, element.board, element.threadNo, element.postIndex);
			}});
			opened = true;
			return false;
		} else if (compareImagePosts(element, imageViewerCurrentImage) > 0) {
			return false;
		}
		return true;
    });
	if (!found)
		console.log('!found');
	console.log('opened: ' + opened);
	console.log('closeIfNotFound: ' + closeIfNotFound);
	if (!opened && closeIfNotFound)
        closeImageViewer();
}

function displayPreviousImage(closeIfNotFound) {
	console.log('displayPreviousImage()');
	var found = false;
	var candidate = null;
	
	if ($('#image-viewer-image').length <= 0 || $('#image-viewer-background').length <= 0) {
        $('#image-viewer').html('');
		return;
	}
	updateImageList();
	$.each(imageList, function (index, element) {
        if (   compareImagePosts(element, imageViewerCurrentImage) == 0
		    && (   element.src == $('#image-viewer-image').attr('src').replace(/\?[0-9]+$/, '')
		        || element.cachedSource == $('#image-viewer-image').attr('src'))) {
			found = true;
			return false;
		} else if (compareImagePosts(element, imageViewerCurrentImage) == 0 && element.available) {
			candidate = element;
		} else if (compareImagePosts(element, imageViewerCurrentImage) > 0) {
			return false;
		}
		return true;
    });
	if (!found)
		console.log('!found');
	if (found && candidate) {
		$('#image-viewer-image').animate({opacity: 0}, {duration: animationDurationForImageViewer, complete: function() {
			$('#image-viewer-image').remove();
			openImageViewer(candidate.src, candidate.service, candidate.board, candidate.threadNo, candidate.postIndex);
		}});
	} else if (closeIfNotFound) {
        closeImageViewer();
	}
}

function addNGImage(url, thumbnailID, md5String) {
	$('#' + thumbnailID).attr('src', imageThumbnailNGSource);
	setTimeout(function() { resizeThumbnail(thumbnailID); }, 0);
	$.ajax({ 
		url: '/api-add-ng-image?url=' + encodeURIComponent(url) + '&md5-string=' + (md5String ? encodeURIComponent(md5String) : ""),
		async: true,
		success: function(result){ 
		},
		error:  function(result, textStatus, errorThrown){
		},
		complete:  function(result, textStatus){
		}
	});
}

function displayImageMenu(event, src, url, thumbnailID, md5String, service, board, threadNo, postIndex) {
	var id = 'image-menu-' + thumbnailID;

	if ($('#' + id).length > 0 || !doesWindowHaveFocus)
		return;
	
	var thumbnailSource = $('#' + thumbnailID).attr('src');
	var isImageSourceInternal =    (src.match(/^\/images\//) || src.match(/^\/image-proxy\?/))
								&& thumbnailSource != imageThumbnailNGSource 
								&& thumbnailSource != imageThumbnailFailedSource 
								&& thumbnailSource != imageThumbnailDownloadFailedSource;
	
	var menuDiv = createPopupMenu(event, id);
	
	if (   thumbnailSource != imageThumbnailNGSource 
	    && thumbnailSource != imageThumbnailFailedSource 
		&& thumbnailSource != imageThumbnailDownloadFailedSource
		&& thumbnailSource != imageThumbnailSpinnerSource) {
		menuDiv.append($("<div />")
						  .attr("class", "popup-menu-item")
						  .mousedown(function(event) { 
						      event.preventDefault();
							  event.stopPropagation(); 
							  $('#' + id).remove(); 
							  openImageViewer(src, service, board, threadNo, postIndex);
						  })
						  .attr('oncontextmenu', 'return false;')
						  .css('background-color', '#C7D7F1')
						  .html("画像ビューアで開く"));
	}
	
	menuDiv.append($("<div />")
			  .attr("class", "popup-menu-item")
			  .mousedown(function(event) {
				  event.preventDefault(); 
				  event.stopPropagation(); 
				  if (isImageSourceInternal) { 
				      open(src, '_blank'); 
				  } else { 
				       openURLInNewWindowWithReferrerDisabled(url); 
				  } 
				  $('#' + id).remove(); 
			  })
			  .attr('oncontextmenu', 'return false;')
			  .html("新しいウィンドウで開く"));
	
	if (isImageSourceInternal) {
		menuDiv.append($("<div />")
				  .attr("class", "popup-menu-item")
				  .mousedown(function(event) {
					  event.preventDefault(); 
					  event.stopPropagation(); 
					  openURLInNewWindowWithReferrerDisabled(url); 
					  $('#' + id).remove(); 
				  })
				  .attr('oncontextmenu', 'return false;')
				  .html("元の画像を新しいウィンドウで開く"));
	}
	
	if (   thumbnailSource != imageThumbnailNGSource 
	    && thumbnailSource != imageThumbnailFailedSource 
		&& thumbnailSource != imageThumbnailDownloadFailedSource
		&& thumbnailSource != imageThumbnailSpinnerSource
		&& (typeof document.createElement('a').download) != "undefined") {
		menuDiv.append($("<div />").attr("class", "popup-menu-divider"));
		menuDiv.append($("<div />")
						  .attr("class", "popup-menu-item")
						  .attr('oncontextmenu', 'return false;')
						  .append($("<a />").attr('href', src)
											.css('width', '100%')
						                    .css('text-decoration', 'none')
						                    .attr('download', url.replace(/^.*\//, ''))
											.click(function(event) { event.stopPropagation(); $('#' + id).remove(); })
						        			.html("画像をファイルに保存")));
	}
	
	createZeroClipboardObjectIfNecessary();
	if (isZeroClipboardAvailable()) {
		menuDiv.append($("<div />").attr("class", "popup-menu-divider"));
		menuDiv.append($("<div />")
				  .attr("id", "popup-menu-item-copy-url")
				  .attr("class", "popup-menu-item")
				  .attr("data-clipboard-text", url)
				  .html('アドレスをコピー'));
		if (md5String) {
			menuDiv.append($("<div />")
						.attr("id", "popup-menu-item-copy-md5-string")
						.attr("class", "popup-menu-item")
						.attr("data-clipboard-text", md5String)
						.html('MD5ハッシュをコピー'))
					.append($("<div />")
						.attr("id", "popup-menu-item-copy-url-and-md5-string")
						.attr("class", "popup-menu-item")
						.attr("data-clipboard-text", url + '\r\n' + md5String + '\r\n')
						.html('アドレスとMD5ハッシュをコピー'));
		}
	}
	
	menuDiv.append($("<div />").attr("class", "popup-menu-divider"));
	menuDiv.append($("<div />")
		.attr("class", "popup-menu-item")
		.mousedown(function(event) { event.preventDefault(); event.stopPropagation(); document.googleForm.submit(); $('#' + id).remove(); })
		.attr('oncontextmenu', 'return false;')
		.html(  '「Google画像検索」で検索'
		      + '<form name="googleForm" action="https://www.google.co.jp/searchbyimage" method="GET" target="_blank">'
		      + '<input type="hidden" name="image_url" value="' + url + '" />'
 			  + '</form>'));
	menuDiv.append($("<div />")
		.attr("class", "popup-menu-item")
		.mousedown(function(event) { event.preventDefault(); event.stopPropagation(); document.ascii2dForm.submit(); $('#' + id).remove(); })
		.attr('oncontextmenu', 'return false;')
		.html(  '「二次元画像詳細検索」で検索'
		      + '<form name="ascii2dForm" action="http://www.ascii2d.net/imagesearch/search" method="POST" target="_blank">'
		      + '<input type="hidden" name="uri" value="' + url + '" />'
 			  + '</form>'));
	menuDiv.append($("<div />")
		.attr("class", "popup-menu-item")
		.mousedown(function(event) { event.preventDefault(); event.stopPropagation(); document.tineyeForm.submit(); $('#' + id).remove(); })
		.attr('oncontextmenu', 'return false;')
		.html(  '「TinEye」で検索'
		      + '<form name="tineyeForm" action="https://www.tineye.com/search" method="POST" target="_blank">'
		      + '<input type="hidden" name="url" value="' + url + '" />'
 			  + '</form>'));
	
	if (thumbnailSource != imageThumbnailNGSource) {
		menuDiv.append($("<div />").attr("class", "popup-menu-divider"));
		menuDiv.append($("<div />")
			.attr("class", "popup-menu-item")
			.mousedown(function(event) { event.preventDefault(); event.stopPropagation(); addNGImage(url, thumbnailID, md5String); $('#' + id).remove(); })
			.attr('oncontextmenu', 'return false;')
			.html('NG画像に指定'));
	}
	
	$("#popup-menus").append(menuDiv);
	$('.popup-menu-item').hover(function(event) { $(this).css('background-color', event.type === 'mouseenter' ? '#C7D7F1' : 'transparent'); return false; });
	keepElementWithinWindow('#' + id, function () {
		displayImageMenu.clipURL = new ZeroClipboard();
		displayImageMenu.clipMD5String = new ZeroClipboard();
		displayImageMenu.clipURLAndMD5String = new ZeroClipboard();
		prepareCopyMenuItem("#popup-menu-item-copy-url", displayImageMenu.clipURL);
		prepareCopyMenuItem("#popup-menu-item-copy-md5-string", displayImageMenu.clipMD5String);
		prepareCopyMenuItem("#popup-menu-item-copy-url-and-md5-string", displayImageMenu.clipURLAndMD5String);
	});
}

function displayImageViewerMenu(event, src, url, thumbnailID, md5String, postIndex) {
	var id = 'image-viewer-menu-' + thumbnailID;

	if ($('#' + id).length > 0 || !doesWindowHaveFocus)
		return;
	
	var thumbnailSource = $('#' + thumbnailID).attr('src');
	var isImageSourceInternal =    (src.match(/^\/images\//) || src.match(/^\/image-proxy\?/))
								&& thumbnailSource != imageThumbnailNGSource 
								&& thumbnailSource != imageThumbnailFailedSource 
								&& thumbnailSource != imageThumbnailDownloadFailedSource;
	
	var menuDiv = createPopupMenu(event, id);
	
	menuDiv.append($("<div />")
		.attr("class", "popup-menu-item")
		.mousedown(function(event) { 
			event.preventDefault(); 
			event.stopPropagation(); 
			if (isImageSourceInternal) { 
				open(src, '_blank'); 
			} else { 
				openURLInNewWindowWithReferrerDisabled(url); 
			} 
			$('#' + id).remove(); 
		})
		.attr('oncontextmenu', 'return false;')
		.html("新しいウィンドウで開く"))

	if (isImageSourceInternal) { 
		menuDiv.append($("<div />")
			.attr("class", "popup-menu-item")
			.mousedown(function(event) { 
				event.preventDefault(); 
				event.stopPropagation(); 
				openURLInNewWindowWithReferrerDisabled(url); 
				$('#' + id).remove(); 
			})
			.attr('oncontextmenu', 'return false;')
			.html("元の画像を新しいウィンドウで開く"))
	}

	menuDiv.append($("<div />").attr("class", "popup-menu-divider"));
	menuDiv.append($("<div />")
		.attr("class", "popup-menu-item")
		.mousedown(function(event) { event.preventDefault(); event.stopPropagation(); displayNextImage(false); $('#' + id).remove(); })
		.attr('oncontextmenu', 'return false;')
		.html("次の画像"))
	menuDiv.append($("<div />")
		.attr("class", "popup-menu-item")
		.mousedown(function(event) { event.preventDefault(); event.stopPropagation(); displayPreviousImage(false); $('#' + id).remove(); })
		.attr('oncontextmenu', 'return false;')
		.html("前の画像"));
	
	if (   thumbnailSource != imageThumbnailNGSource 
	    && thumbnailSource != imageThumbnailFailedSource 
		&& thumbnailSource != imageThumbnailDownloadFailedSource
		&& thumbnailSource != imageThumbnailSpinnerSource
		&& (typeof document.createElement('a').download) != "undefined") {
		menuDiv.append($("<div />").attr("class", "popup-menu-divider"));
		menuDiv.append($("<div />")
						  .attr("class", "popup-menu-item")
						  .attr('oncontextmenu', 'return false;')
						  .append($("<a />").attr('href', src)
											.css('width', '100%')
						                    .css('text-decoration', 'none')
						                    .attr('download', url.replace(/^.*\//, ''))
											.click(function(event) { event.stopPropagation(); $('#' + id).remove(); })
						        			.html("画像をファイルに保存")));
	}
	
	createZeroClipboardObjectIfNecessary();
	if (isZeroClipboardAvailable()) {
		menuDiv.append($("<div />").attr("class", "popup-menu-divider"));
		menuDiv.append($("<div />")
					.attr("id", "popup-menu-item-copy-url")
					.attr("class", "popup-menu-item")
					.attr("data-clipboard-text", url)
					.html('アドレスをコピー'));
		if (md5String) {
			menuDiv.append($("<div />")
						.attr("id", "popup-menu-item-copy-md5-string")
						.attr("class", "popup-menu-item")
						.attr("data-clipboard-text", md5String)
						.html('MD5ハッシュをコピー'))
					.append($("<div />")
						.attr("id", "popup-menu-item-copy-url-and-md5-string")
						.attr("class", "popup-menu-item")
						.attr("data-clipboard-text", url + '\r\n' + md5String + '\r\n')
						.html('アドレスとMD5ハッシュをコピー'));
		}
	}
	
	menuDiv.append($("<div />").attr("class", "popup-menu-divider"));
	menuDiv.append($("<div />")
		.attr("class", "popup-menu-item")
		.mousedown(function(event) { event.preventDefault(); event.stopPropagation(); document.googleForm.submit(); $('#' + id).remove(); })
		.attr('oncontextmenu', 'return false;')
		.html(  '「Google画像検索」で検索'
		      + '<form name="googleForm" action="https://www.google.co.jp/searchbyimage" method="GET" target="_blank">'
		      + '<input type="hidden" name="image_url" value="' + url + '" />'
 			  + '</form>'));
	menuDiv.append($("<div />")
		.attr("class", "popup-menu-item")
		.mousedown(function(event) { event.preventDefault(); event.stopPropagation(); document.ascii2dForm.submit(); $('#' + id).remove(); })
		.attr('oncontextmenu', 'return false;')
		.html(  '「二次元画像詳細検索」で検索'
		      + '<form name="ascii2dForm" action="http://www.ascii2d.net/imagesearch/search" method="POST" target="_blank">'
		      + '<input type="hidden" name="uri" value="' + url + '" />'
 			  + '</form>'));
	menuDiv.append($("<div />")
		.attr("class", "popup-menu-item")
		.mousedown(function(event) { event.preventDefault(); event.stopPropagation(); document.tineyeForm.submit(); $('#' + id).remove(); })
		.attr('oncontextmenu', 'return false;')
		.html(  '「TinEye」で検索'
		      + '<form name="tineyeForm" action="https://www.tineye.com/search" method="POST" target="_blank">'
		      + '<input type="hidden" name="url" value="' + url + '" />'
 			  + '</form>'));

	if (thumbnailSource != imageThumbnailNGSource) {
		menuDiv.append($("<div />").attr("class", "popup-menu-divider"));
		menuDiv.append($("<div />")
			.attr("class", "popup-menu-item")
			.mousedown(function(event) { event.preventDefault(); event.stopPropagation(); addNGImage(url, thumbnailID, md5String); closeImageViewer(event); $('#' + id).remove(); })
			.attr('oncontextmenu', 'return false;')
			.html('NG画像に指定'));
	}

	menuDiv.append($("<div />").attr("class", "popup-menu-divider"));
	menuDiv.append($("<div />")
		.attr("class", "popup-menu-item")
		.mousedown(function(event) { event.preventDefault(); event.stopPropagation(); closeImageViewer(event); $('#' + id).remove(); })
		.attr('oncontextmenu', 'return false;')
		.html("閉じる"))
	
	$("#popup-menus").append(menuDiv);
	$('.popup-menu-item').hover(function(event) { $(this).css('background-color', event.type === 'mouseenter' ? '#C7D7F1' : 'transparent'); return false; });
	keepElementWithinWindow('#' + id, function() {
		displayImageViewerMenu.clipURL = new ZeroClipboard();
		displayImageViewerMenu.clipMD5String = new ZeroClipboard();
		displayImageViewerMenu.clipURLAndMD5String = new ZeroClipboard();
		prepareCopyMenuItem("#popup-menu-item-copy-url", displayImageViewerMenu.clipURL);
		prepareCopyMenuItem("#popup-menu-item-copy-md5-string", displayImageViewerMenu.clipMD5String);
		prepareCopyMenuItem("#popup-menu-item-copy-url-and-md5-string", displayImageViewerMenu.clipURLAndMD5String);
	});
	// $('#' + id).css({'opacity': 0.95});
}

function prepareCopyMenuItem(selector, clip) {
	setTimeout(function() {
		clip.on('aftercopy', function(event) {
			clip.unclip();
			ZeroClipboard.destroy();
			ZeroClipboard.create();
			$(event.target).parent().remove();
		});
		clip.clip($(selector));
	}, 0);
}




////////////////////
// FAVORITE BOARD //
////////////////////

var isFavoriteBoardBeingDragged = false;

function favoriteBoardListStartDragging(event, ui) { 
	if (previousLoadFavoriteBoardListRequest)
		previousLoadFavoriteBoardListRequest.abort();
	previousLoadFavoriteBoardListRequest = false;
	isFavoriteBoardBeingDragged = true;
	$('.bbs-menu-item').unbind('mouseenter mouseleave').css({ 'background-color': 'transparent' });
	ui.helper.css({'z-index':1, 'background-color': '#C7D7F1' });
	ui.helper.css("opacity", "0.80");
	/* ui.helper.css({'background':'transparent'}); */ 
}

function favoriteBoardListStopDragging(event, ui) {
	ui.helper.css("opacity", "1");
	$('#favorite-board-list > div').each(function (index, element) { $(element).draggable("disable"); });
	$('#favorite-board-list > script').remove();
	$(event.target).css({ 'left': '', 'top': '', 'z-index':0, 'background-color': (isMouseCursorOnElement(event.target) ? '#C7D7F1'  : 'transparent') });
	$('.bbs-menu-item').hover(function(event) { $(this).css('background-color', event.type === 'mouseenter' ? '#C7D7F1' : 'transparent'); });
	
	var oldPosition = $('#favorite-board-list').children('div').index(event.target);
	var distance = ui.offset.top - $('#favorite-board-list').offset().top - parseInt($('#favorite-board-list').css('padding-top').replace('px', ""));
	var newPosition = oldPosition;
	
	if  (distance <= 1) {
		$('#favorite-board-list').prepend($(event.target).detach());
		newPosition = 0;
	} else {
		// newPosition = Math.floor(distance / $(event.target).height());
		$('#favorite-board-list').children('div').each(function (index, element) {
			if (element == event.target) {
				return true;
			} else if ($(this).offset().top <= ui.offset.top &&  ui.offset.top < $(this).offset().top + $(this).height()) {
				newPosition = index;
				return false;
			} else {
				return true;
			}
		});
		var indexAfter;
		
		if (oldPosition == newPosition) {
			isFavoriteBoardBeingDragged = false;
			$('#favorite-board-list > div').each(function (index, element) { $(element).draggable("enable"); });
			return;
		}
		if (oldPosition == newPosition + 1) {
			indexAfter = newPosition + 2;
		} else {
			indexAfter = newPosition + 1;
		}
		if (newPosition < oldPosition)
			++newPosition;
		
		if ($('#favorite-board-list').children().eq(indexAfter).length > 0) {
			$('#favorite-board-list').children().eq(indexAfter).before($(event.target).detach());
		} else {
			$('#favorite-board-list').append($(event.target).detach());
		}
	}
	
	$.ajax({ 
		url: '/api-move-favorite-board?old-position=' + oldPosition + '&new-position=' + newPosition,
		async: true,
		success: function(result){ 
		},
		error:  function(result, textStatus, errorThrown){
		},
		complete:  function(result, textStatus){
			isFavoriteBoardBeingDragged = false;
			$('#favorite-board-list > div').each(function (index, element) { $(element).draggable("enable"); });
		}
	});
}



//////////////
// SETTINGS //
//////////////

var adminSettingsWindowWidth  = 428;
var adminSettingsWindowHeight = 119;

window.setAdminSettingsWindowSize = function (width, height) {
	// alert(width + ', ' + height);
	adminSettingsWindowWidth  = width;
	adminSettingsWindowHeight = height;
}

function openAdminSettingsWindow() {
	var adminSettingsWindow = open('/admin-settings',
							randomElementID(),
							centerPopupWindow(adminSettingsWindowWidth, adminSettingsWindowHeight) + ", resizable  = no, scrollbars = no");
	adminSettingsWindow.focus();
}

var userSettingsWindowWidth  = 428;
var userSettingsWindowHeight = 274;

window.setUserSettingsWindowSize = function (width, height) {
	// alert(width + ', ' + height);
	userSettingsWindowWidth  = width;
	userSettingsWindowHeight = height;
}

function openUserSettingsWindow() {
	var userSettingsWindow = open('/user-settings',
						           randomElementID(),
							       centerPopupWindow(userSettingsWindowWidth, userSettingsWindowHeight) + ", resizable  = no" + ", scrollbars = no");
	userSettingsWindow.focus();
}

var imageDownloadSettingsWindowWidth  = 428;
var imageDownloadSettingsWindowHeight = 119;

window.setImageDownloadWindowSize = function (width, height) {
	// alert(width + ', ' + height);
	imageDownloadSettingsWindowWidth  = width;
	imageDownloadSettingsWindowHeight = height;
}

function openImageDownloadSettingsWindow() {
	var imageDownloadSettingsWindow = open('/image-download-settings',
						                   randomElementID(),
					 		               centerPopupWindow(imageDownloadSettingsWindowWidth, imageDownloadSettingsWindowHeight) + ", resizable  = no" + ", scrollbars = no");
	imageDownloadSettingsWindow.focus();
}

var abornFiltersWindowWidth  = 428;
var abornFiltersWindowHeight = 274;

function openAbornFiltersWindow() {
	var abornFiltersWindow = open('/aborn-filters',
					   	           randomElementID(),
					 		       centerPopupWindow(abornFiltersWindowWidth, abornFiltersWindowHeight) + ", resizable  = no" + ", scrollbars = no");
	abornFiltersWindow.focus();
}

var abornPostsWindowWidth  = 428;
var abornPostsWindowHeight = 274;

function openAbornPostsWindow() {
	var abornPostsWindow = open('/aborn-posts',
						         randomElementID(),
					 		     centerPopupWindow(abornPostsWindowWidth, abornPostsWindowHeight) + ", resizable  = no" + ", scrollbars = no");
	abornPostsWindow.focus();
}

var abornIDsWindowWidth  = 428;
var abornIDsWindowHeight = 274;


function openAbornIDsWindow() {
	var abornIDsWindow = open('/aborn-ids',
						       randomElementID(),
					 		   centerPopupWindow(abornIDsWindowWidth, abornIDsWindowHeight) + ", resizable  = no" + ", scrollbars = no");
	abornIDsWindow.focus();
}

function updateSystemSetting(settingName, value)
{
	$.ajax({
		url: '/api-update-system-setting?setting-name=' + encodeURIComponent(settingName) + '&value=' + encodeURIComponent(value),
		async: true
	});
}

function updateUserSetting(settingName, value)
{
	$.ajax({
		url: '/api-update-user-setting?setting-name=' + encodeURIComponent(settingName) + '&value=' + encodeURIComponent(value),
		async: true
	});
}



////////////////////
// INITIALIZATION //
////////////////////

function autoloadFavoriteBoardList() {
	if ($('#automatic-updates-for-favorite-board-list-checkbox').is(':checked'))
	    loadFavoriteBoardList(true);
	setTimeout(autoloadFavoriteBoardList, 60000);
}

function autoloadSpecialMenu() {
	if ($('#automatic-updates-for-special-menu-checkbox').is(':checked'))
	    loadSpecialMenu(true);
	setTimeout(autoloadSpecialMenu, 10 * 60000);
}

$(document).ready(function() {
	$('#special-menu').html('<p><br><br>' + ajaxSpinnerBars + '</p>');
	$('#favorite-board-list').html('<p><br><br>' + ajaxSpinnerBars + '</p>');
	$('#server-info-panel').html('<p><br><br>' + ajaxSpinnerBars + '</p>');
	$('#download-status-panel').html('<p><br><br>' + ajaxSpinnerBars + '</p>');

	loadSpecialMenu(false);
	autoloadSpecialMenu();

	loadFavoriteBoardList(false);
	autoloadFavoriteBoardList();

	updateServerInfoPanel();
	updateDownloadStatusPanel();
}); 
