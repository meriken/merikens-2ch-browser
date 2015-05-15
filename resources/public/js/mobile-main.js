var adjustComponentsInterval = 200;
var isImageBeingDragged      = false;
var imageViewerPostIndex     = 1; // dummy



/********/
/* AJAX */
/********/

/*
$.ajaxSetup({
  cache: false
});
*/


/*****************/
/* ZeroClipboard */
/*****************/

/*
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
*/



/*********************/
/* UTILITY FUNCTIONS */
/*********************/

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

function removeOptionsFromThreadURL(threadURL) {
	return threadURL.replace(/[0-9ln,+-]+ *$/, "");
}

function createElementIdForPost(service, board, threadNo, postIndex, elementType) {
	// return ('post-' + elementType + '-' + service + '-' + board + '-' + threadNo + '-' + postIndex).replace(/[.\/]/g, '_');
	return ('res-' + postIndex + '-' + elementType).replace(/[.\/]/g, '_');
}

function createSelectorForPost(service, board, threadNo, postIndex, elementType) {
	var sel = '#' + createElementIdForPost(service, board, threadNo, postIndex, elementType);
	// console.log('createSelectorForPost(): ' + sel);
	return sel;
}



function adjustComponents() {
	if (!isImageBeingDragged)
		setImageViewerSize();

	if ($('#image-viewer-image').length > 0 || $('#image-viewer-background').length > 0)
		return;
	
	showThumbnails();
	
	$('li > a > span').each(function()
	{
		if($(this).find('.ui-li-count').length == 2)
		{
			var first = $(this).find('.ui-li-count:first');
			var second = $(this).find('.ui-li-count:nth(1)');
			var firstPos = second.position().left - first.outerWidth() - 5;
			first.css('left', firstPos).css('right', 'auto');
		}
	});
}

function updateCounts() {
	// console.log('updateCounts()');
	$.ajax({ 
		url: './api-mobile-get-favorite-board-counts', 
		async: true,
		dataType: 'json',
        cache: false,
		success: function(result) { 
			$('.favorite-board-counts').html('');
			$.each(result, function(index, value) {
				var elementClass = ('favorite-board-counts-' + value.service + '-' + value.board).replace(/[.\/]/g, '_');

				var newThreadBubble = $("<span />").attr("class", "ui-li-count favorite-board-list new-threads");
				if (value.newThreads <= 0) 
					newThreadBubble.addClass("zero");
				$('.' + elementClass).append(newThreadBubble.html(value.newThreads));
				
				var newPostBubble = $("<span />").attr("class", "ui-li-count favorite-board-list new-posts");
				if (value.newPosts <= 0) 
					newPostBubble.addClass("zero");
				$('.' + elementClass).append(newPostBubble.html(value.newPosts));
			});
			adjustComponents();
		},
		error:  function(result, textStatus, errorThrown){
		},
		complete: function(result, textStatus) {
			setTimeout(updateCounts, 10000);
		}
	});
}

function removeDomCachesForBoards() {
	$('div.ui-page.board').each(function(index, element) {
        $(element).remove();
    });
}

function removeDomCachesForThreads() {
	$('div.ui-page.thread').each(function(index, element) {
        $(element).remove();
    });
}

function removeDomCachesForFavoriteBoards() {
	$('div.ui-page.favorite-boards').each(function(index, element) {
        $(element).remove();
    });
}



////////////
// IMAGES //
////////////

var maxNumberOfImageDownloads = 4;

var unloadedThumbnailList = [];
var loadedThumbnailList = [];
var imageList = [];

function resetThumbnailLists() {
	unloadedThumbnailList = [];
	loadedThumbnailList = [];
}

function addThumbnail(id, src) {
	console.log("addThumbnail(" + id + ", " + src + ");");
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
	var count = maxNumberOfImageDownloads;
	
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
	if ($('#image-viewer-image').length <= 0 || $('#image-viewer-background').length <= 0 || isImageBeingDragged)
		return;
	
	// console.log('setImageViewerSize()');
	
	var imageViewerHeight = (typeof window.innerHeight != 'undefined') ? window.innerHeight : $(window).height();
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
	
	if (height > imageViewerHeight) {
		width *= imageViewerHeight / height;
		height = imageViewerHeight;
	}	
	
	$('#image-viewer-background')
	    .html('')
		.css('left', 0)       
		.css('top', $('body').scrollTop())         
		.css('width', $(window).width() + 'px')     
		.css('height', imageViewerHeight + 'px');
	$('#image-viewer-image')
		.css('left', ($(window).width() - width) / 2)       
		.css('top', $('body').scrollTop() + (imageViewerHeight - height) / 2)
		.css('width', width + 'px')     
		.css('height', height + 'px')
								.css('display', 'block');
}


function showThumbnails() {
	var actualWindowHeight = (typeof window.innerHeight != 'undefined') ? window.innerHeight : $(window).height();

	if ($('#thread-content-wrapper').length > 0) {
		$("img.thumbnail").each(function (index, thumbnail) {
			// console.log($('body').scrollTop() + ', ' + $(thumbnail).offset().top + ', ' + ($(thumbnail).offset().top  + $(thumbnail).height()) + ', ' + $('body').scrollTop() + actualWindowHeight);
			if (   $('body').scrollTop() <= $(thumbnail).parent().offset().top + $(thumbnail).parent().height()
			    && $(thumbnail).parent().offset().top  < $('body').scrollTop() + actualWindowHeight) {
				$(thumbnail).css('display', 'inline');
			} else {
				$(thumbnail).css('display', 'none');
			}
		});
	
	}
}

function startOpeningAnimationForImageViewer() {
	if (document.getElementById('image-viewer-image').naturalWidth) {
		isImageBeingDragged = false;
		$("#image-viewer-image").animate({opacity: 1}, {duration: animationDurationForImageViewer});
	} else {
		setTimeout(startOpeningAnimationForImageViewer, 100);
	}
}

function openImageViewer(src, postIndex) {
	$("[data-position='fixed']").hide();
	// $("[data-role='page']").hide();
	
	setTimeout(function() {
		var cachedSource = src;
		var thumbnailID = null;
		var url = null;
		var md5String = null;
		
		$('body').bind('touchmove', function(event) { event.preventDefault(); });
		
		if ($('#image-viewer-background').length <= 0) {
			$('#image-viewer').css('opacity', 0);
			$('#image-viewer').animate({opacity: 1}, {duration: animationDurationForImageViewer});
		}
		
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
		// console.log(cachedSource);
		
		var imageViewerHeight = (typeof window.innerHeight != 'undefined') ? window.innerHeight : $(window).height();
		var background = $('<div />')
							  .attr('id', 'image-viewer-background')
							  .css('background', 'black')       
							  .css('opacity', '0.7')
							  .css('position', 'absolute')       
							.css('left', 0)       
							.css('top', $('body').scrollTop())         
							.css('width', $(window).width() + 'px')     
							.css('height', imageViewerHeight + 'px')
							  .mousedown(function(event) {
								  event.preventDefault();
								  if (event.which == 3) {
									  displayImageViewerMenu(event, src, url, thumbnailID, md5String, postIndex);
								  } else {
									  closeImageViewer(event);
								  }
							  })
							  .attr('oncontextmenu', 'return false;');
	
		var image = $('<img />')
						.attr('id', 'image-viewer-image')
						.attr('tabindex', '1')
						.css('display', 'none')
						.css('position', 'absolute')       
						.css('opacity', 0)
						// .animate({opacity: 1})
						//.bind('load', function() {
						//	$(this).animate({opacity: 1});
						//})				
					/*       
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
						*/
						.attr('oncontextmenu', 'return false;')
						.attr('src', cachedSource);
		setTimeout(startOpeningAnimationForImageViewer, 100);

		if ($('#image-viewer-background').length <= 0) {
			$('#image-viewer').append(background.html(ajaxSpinnerBarsWhite));
		} else {
			$('#image-viewer-background').html(ajaxSpinnerBarsWhite);
		}
		$('#image-viewer').append(image);
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
		$( "#image-viewer-image" ).draggable({
			axis: 'x',
			start: function( event, ui ) {
				// event.preventDefault();
				event.stopPropagation();

				isImageBeingDragged = true;
			},
			stop: function( event, ui ) {
				// event.preventDefault();
				event.stopPropagation();

				var imageViewerHeight = (typeof window.innerHeight != 'undefined') ? window.innerHeight : $(window).height();
				var width = document.getElementById('image-viewer-image').naturalWidth;
				var height = document.getElementById('image-viewer-image').naturalHeight;
				
				if (!width || !height) {
					setTimeout(function (instance) { setImageViewerSize(); }, 100);
					isImageBeingDragged = false;
					return;
				}
				
				if (width > $(window).width()) {
					height *= $(window).width() / width;
					width = $(window).width();
				}
				
				if (height > imageViewerHeight) {
					width *= imageViewerHeight / height;
					height = imageViewerHeight;
				}	
				var left = ($(window).width() - width) / 2;
				var top  = $('body').scrollTop() + (imageViewerHeight - height) / 2;
				
				var currentLeft = parseInt($( "#image-viewer-image" ).css('left'));
				if (currentLeft < left && left - currentLeft > $( "#image-viewer-background" ).width() / 5) {
					$( "#image-viewer-image" ).animate(
						{left: -width - 1},
						{queue: false,
						 duration: ($( "#image-viewer-background" ).width() - left + currentLeft) * 1, 
						 complete: function() { displayNextImage(true, false); }});
				} else if (left < currentLeft && currentLeft - left > $( "#image-viewer-background" ).width() / 5 && isTherePreviousImage()) {
					$( "#image-viewer-image" ).animate(
						{left: $( "#image-viewer-background" ).width()}, 
						{queue: false,
						 duration: ($( "#image-viewer-background" ).width() - currentLeft + left) * 1, 
						 complete: function() { displayPreviousImage(false); }});
					
				} else {
					$( "#image-viewer-image" ).animate({left: left}, {duration: ($( "#image-viewer-background" ).width() - Math.abs(currentLeft - left)) * 1, complete: function() { isImageBeingDragged = false; }});
					
				}
			}
		});
		$( "#image-viewer-image" ).click(function(event) {
			event.preventDefault();
			displayNextImage(true);
		})
		
		imageViewerPostIndex = postIndex;
		// setTimeout(function (instance) { setImageViewerSize(); }, 100);
	}, 0);
}

function reallyCloseImageViewer() {
	console.log('reallyCloseImageViewer()');
	$('#image-viewer-image').remove();
	$('#image-viewer-background').remove();
	$('body').unbind('touchmove');
	$("[data-position='fixed']").show();
	console.log('isImageBeingDragged = false;'); isImageBeingDragged = false;
}

function closeImageViewer() {
	console.log('closeImageViewer()');
	$('#image-viewer').animate({opacity: 0}, {duration: animationDurationForImageViewer, complete: reallyCloseImageViewer});
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

function updateImageList() {
	imageList.sort(function(a, b) { return (a.postIndex != b.postIndex) ? (a.postIndex - b.postIndex) : (a.index - b.index); });
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

function displayNextImage(closeIfNotFound, transition) {
	if (typeof transition == 'undefined') transition = true;
	
	console.log('displayNextImage()');
	var found = false;
	var opened = false;
	
	if ($('#image-viewer-image').length <= 0 || $('#image-viewer-background').length <= 0) {
        $('#image-viewer').html('');
		isImageBeingDragged = false;
		return;
	}
	updateImageList();
	$.each(imageList, function (index, element) {
		/*
		if (element.postIndex == imageViewerPostIndex) {
			console.log('element.src:                          ' + element.src);
			console.log('element.cachedSource:                 ' + element.cachedSource);
			console.log("$('#image-viewer-image').attr('src'): " + $('#image-viewer-image').attr('src'));
		}
		*/
        if (   element.postIndex == imageViewerPostIndex
		    && (   element.src == $('#image-viewer-image').attr('src').replace(/\?[0-9]+$/, '')
		        || element.cachedSource == $('#image-viewer-image').attr('src'))) {
			found = true;
		} else if (found && element.postIndex == imageViewerPostIndex && element.available) {
			if (transition) {
				$('#image-viewer-image').animate({opacity: 0}, {duration: animationDurationForImageViewer, complete: function() { 
					$('#image-viewer-image').remove();
					openImageViewer(element.src, element.postIndex);
				}});
			} else {
				$('#image-viewer-image').remove();
				openImageViewer(element.src, element.postIndex);
			}
			opened = true;
			return false;
		} else if (element.postIndex > imageViewerPostIndex) {
			return false;
		}
		return true;
    });
	if (!found)
		console.log('!found');
	if (!opened && closeIfNotFound) {
       closeImageViewer();
	} else {
		isImageBeingDragged = false;
	}
}

function isTherePreviousImage() {
	var found = false;
	var candidate = null;
	
	if ($('#image-viewer-image').length <= 0 || $('#image-viewer-background').length <= 0)
        return false;
	
	updateImageList();
	$.each(imageList, function (index, element) {
        if (   element.postIndex == imageViewerPostIndex
		    && (   element.src == $('#image-viewer-image').attr('src').replace(/\?[0-9]+$/, '')
		        || element.cachedSource == $('#image-viewer-image').attr('src'))) {
			found = true;
			return false;
		} else if (element.postIndex == imageViewerPostIndex && element.available) {
			candidate = element;
		} else if (element.postIndex > imageViewerPostIndex) {
			return false;
		}
		return true;
    });
	return (found && candidate);
}

function displayPreviousImage(closeIfNotFound) {
	console.log('displayPreviousImage()');
	var found = false;
	var candidate = null;
	
	if ($('#image-viewer-image').length <= 0 || $('#image-viewer-background').length <= 0) {
        $('#image-viewer').html('');
		isImageBeingDragged = false;
		return;
	}
	updateImageList();
	$.each(imageList, function (index, element) {
        if (   element.postIndex == imageViewerPostIndex
		    && (   element.src == $('#image-viewer-image').attr('src').replace(/\?[0-9]+$/, '')
		        || element.cachedSource == $('#image-viewer-image').attr('src'))) {
			found = true;
			return false;
		} else if (element.postIndex == imageViewerPostIndex && element.available) {
			candidate = element;
		} else if (element.postIndex > imageViewerPostIndex) {
			return false;
		}
		return true;
    });
	if (!found)
		console.log('!found');
	if (found && candidate) {
        $('#image-viewer-image').remove();
		openImageViewer(candidate.src, candidate.postIndex);
		isImageBeingDragged = false;
	} else if (closeIfNotFound) {
       closeImageViewer();
	} else {
		isImageBeingDragged = false;
	}
}



////////////
// EVENTS //
////////////

// $(document).ready(function() {
var wasPageInitialized = false;

$( document ).on("pagecontainershow", function( event ) {
	if (!wasPageInitialized) {
		setInterval(adjustComponents, adjustComponentsInterval);
		updateCounts();
		wasPageInitialized = true;
	}
	jumpToPost(); 
});

// $( window ).load(function() { jumpToPost(); });
// $( document ).on("pagecontainerload", function( event ) { alert('pagecontainerload'); jumpToPost(); });
$( document ).on("pagecontainershow", function( event ) { });

/*
$( document ).on("pagebeforechange", function( event ) {
    $.mobile.defaultHomeScroll = 0;
    jumpToPost = function() {}
});
*/


/*
$( document ).on("pagecontainershow", function( event ) {
	showThumbnails(); 
});

jQuery( window ).on( "scrollstop", function( event ) { showThumbnails(); });

jQuery( window ).on( "orientationchange", function( event ) {
	setImageViewerSize();
	setTimeout(setImageViewerSize, 100);
	setTimeout(setImageViewerSize, 200);
	setTimeout(setImageViewerSize, 300);
	setTimeout(setImageViewerSize, 400);
	setTimeout(setImageViewerSize, 500);
});
*/