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