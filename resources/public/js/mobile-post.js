var threadURL = null;
var autosaveInterval = 10000;



$(document).ready(function() {
}); 

$(window).resize(function() {
});

function autosaveDraft() {
	$.ajax({ 
		url: '/api-autosave-draft'
		     + '?thread-url=' + encodeURIComponent(threadURL)
		     + '&handle='     + encodeURIComponent($("#post-page-handle").val())
		     + '&email='      + encodeURIComponent($("#post-page-email").val())
		     + '&draft='      + encodeURIComponent($("#post-page-message").val()),
		async: false,
		success: function(result){ 
		},
		error:  function(result, textStatus, errorThrown){
		}
	});
	setTimeout(autosaveDraft, autosaveInterval);
}

/*
$(document).ready(function() 
    { 
		setTimeout(autosaveDraft, autosaveInterval);
	} 
); 
*/