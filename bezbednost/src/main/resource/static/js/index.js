function json(res) {
    return res.json();
}
 

function certTableRow(cert) {
    return `
        <tr>
            <td>${cert.commonName}</td>
            <td>${cert.givenname}</td>
            <td>${cert.surname}</td>
            <td>${cert.organization}</td>
            <td>${cert.organizationUnit}</td>
            <td>${cert.country}</td>
            <td>${cert.email}</td>
            <td>
            <button data-serial-number="${cert.serial}">Revoke</button>
                <button data-serial-number="${cert.serial}">Check</button>
                
            </td>
        </tr>
    `;
};
document.getElementById('certT').addEventListener('click', e => {
        fetch('api/certificate/revoke/' + e.target.dataset.serialNumber, {
            method: 'put',
            credentials: 'include'
        }).then(res => {
        	if(res.ok){ 
        		
        	}else{
        		alert("Niste ovlasceni!")
        	}
        });
   
});
document.getElementById('certT').addEventListener('click', e => {
        fetch('api/certificate/check/' + e.target.dataset.serialNumber, { 
        	credentials: 'include' 
        }) 
            .then(res => {
                if (res.ok) {
                    alert('Sertifikat je validan');
                } else {
                    alert('Nije validan');
                }
            });
   
});
 
fetch('/api/certificate', {
    credentials: 'include' 
}).then(json).then(certificates => { 
    const table = document.getElementById('certT');
 
    for (let cert of certificates) {
        table.innerHTML += certTableRow(cert);
    }
});


