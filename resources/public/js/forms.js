function formhash_newelement(form, password_value, name, hash_func) {
	if (typeof hash_func == 'undefined') hash_func = hex_sha512;
	
	var p = document.createElement("input");
	form.appendChild(p);
	p.name = name;
	p.type = "hidden";
	try {
		p.value = hash_func.apply(null, [password_value]);
	} catch (e) {
		p.value = "";
	}
	// alert(password_value);
	// alert(toBinary(password_value));
	// alert(sha512(password_value));
	// alert(hex_sha512(password_value));
	// alert(p.value);
}

function formhash(form, password) {
	try {
		formhash_newelement(form, password.value, "p");
		formhash_newelement(form, password.value, "pp", sha512);
		password.value = "";
		formhash_newelement(form, "test", "t");
		formhash_newelement(form, "test", "tt", sha512);
		form.submit();
	} catch (e) {
		var vDebug = ""; 
		for (var prop in e) 
		{  
		   vDebug += "property: "+ prop+ " value: ["+ e[prop]+ "]\n"; 
		} 
		vDebug += "toString(): " + " value: [" + e.toString() + "]"; 
		status.rawValue = vDebug; 
				alert(vDebug);
	}
    return false;
}

function regformhash(form, password, retyped_password) {
	formhash_newelement(form, password.value, "p");
    password.value = "";
	formhash_newelement(form, retyped_password.value, "pp");
    retyped_password.value = "";
	formhash_newelement(form, "test", "t");
	form.submit();
    return false;
}

function toBinary(input) {
  var  output = "";
    for (i=0; i < input.length; i++) {
        output +=input[i].charCodeAt(0).toString(16) + " ";
    }
	return output;
}