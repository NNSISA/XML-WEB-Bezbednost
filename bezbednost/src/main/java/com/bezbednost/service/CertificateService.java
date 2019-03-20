package com.bezbednost.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.DatatypeConverter;

import org.bouncycastle.asn1.x500.X500Name;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bezbednost.certificate.Certificate;
import com.bezbednost.certificate.CertificateDTO;
import com.bezbednost.certificate.CertificateGenerator;
import com.bezbednost.certificate.IssuerData;
import com.bezbednost.certificate.SubjectData;
import com.bezbednost.repository.KSRepository;

@Service
public class CertificateService {
	 
	private final CertificateGenerator certificateGenerator; 
	@Autowired
	private final KSRepository repository; 
	
	 
	@Autowired
	public CertificateService(CertificateGenerator certificateGenerator, KSRepository repository) {
		this.certificateGenerator = certificateGenerator;
		this.repository = repository;
	}

	public X509Certificate getOne(String serial) {
		return repository.getCertificate(serial).orElseThrow(RuntimeException::new);
	} 
	
	public List<CertificateDTO> getAll() {
		return repository.getCertificates().stream().map(CertificateDTO::new).collect(Collectors.toList());
	}
	public List<CertificateDTO> getAllCa() {
		return repository.getCertificates().stream().filter(c -> c.getBasicConstraints() != -1)
			.map(CertificateDTO::new)
			.collect(Collectors.toList());
	}
	
	public CertificateDTO add(Certificate cert) {		
		try {
			X500Name x500name = KeyGenerator.generateX500Name(cert);
			KeyPair keyPair = KeyGenerator.generateKeyPair();

			SubjectData subjectData = new SubjectData(keyPair.getPublic(), x500name, new Date(), cert.getEndDate());
			IssuerData issuerData = new IssuerData(keyPair.getPrivate(), x500name);

			X509Certificate certificate = certificateGenerator.generateCertificate(subjectData, issuerData, true);	

			repository.saveCertificate(certificate.getSerialNumber().toString(), keyPair.getPrivate(), certificate);
		
			return new CertificateDTO(certificate);
		} catch(Exception e) {
			e.printStackTrace(); 
			throw new RuntimeException();
		}
	}
	public X509Certificate issueCertificate(String issuerSerial, Certificate cert, boolean isCa) {
		IssuerData issuerData = repository.getIssuerData(issuerSerial);
		
		if (issuerData == null || getOne(issuerSerial).getBasicConstraints() == -1) {
			throw new RuntimeException();
		}

		try {
			KeyPair keyPair = KeyGenerator.generateKeyPair();
			X500Name x500name = KeyGenerator.generateX500Name(cert);
			SubjectData subjectData = new SubjectData(keyPair.getPublic(), x500name, new Date(), cert.getEndDate());			
			X509Certificate certificate = certificateGenerator.generateCertificate(subjectData, issuerData, isCa);
			repository.saveCertificate(certificate.getSerialNumber().toString(), keyPair.getPrivate(), certificate);
			return certificate;
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
	public void revokeCertificate(String serial) {
		X509Certificate certificate = getOne(serial);
		List<X509Certificate> certificates = new ArrayList<>();
		
		try {
			File file = new File("./crl.crl");
			
			if (!file.exists()) {		
				saveCRL(certificates, file);
			} else {
				ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
				certificates = (List<X509Certificate>) ois.readObject();
				ois.close();
			}
			
			for (X509Certificate cert : certificates) {
				if (cert.getSerialNumber().equals(certificate.getSerialNumber())) {
					return;
				}
			}
			
			String issuer = certificate.getSubjectX500Principal().getName();
			
			List<X509Certificate> allCertificates = repository.getCertificates();
			
			List<X509Certificate> revokeList = allCertificates
				.stream()
				.filter(c -> c.getIssuerX500Principal().getName().equals(issuer))
				.collect(Collectors.toList());
			
			allCertificates.removeAll(revokeList);	
			recursion(certificates, revokeList, allCertificates);
			
			certificates.add(certificate);
			certificates.addAll(revokeList);
			saveCRL(certificates, file);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	} 
	
	private void saveCRL(List<X509Certificate> certificates, File file) throws Exception {
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
		oos.writeObject(certificates);
		oos.flush();
		oos.close();
	}
	public void recursion(List<X509Certificate> certificates, List<X509Certificate> revokeList, List<X509Certificate> allCertificates) {		
		revokeList.forEach(rc -> {
			List<X509Certificate> childRevokeList = allCertificates
				.stream()
				.filter(c -> c.getIssuerX500Principal().getName().equals(rc.getSubjectX500Principal().getName()))
				.collect(Collectors.toList());
			
			certificates.addAll(childRevokeList);
			allCertificates.removeAll(childRevokeList);
			recursion(certificates, childRevokeList, allCertificates);
		});
	}
	public boolean IsValid(String serial) {
		X509Certificate cert = getOne(serial);
		List<X509Certificate> certs = new ArrayList<>();
		File f = new File("./crl.crl");
		
		if (!f.exists()) {
			return true;
		}
		
		try {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
			certs = (List<X509Certificate>) ois.readObject();
			ois.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			cert.checkValidity();
		} catch (CertificateExpiredException | CertificateNotYetValidException  e) {
			 
			return false;
		} 
		
		for (X509Certificate cert0 : certs) {
			if (cert0.getSerialNumber().equals(cert.getSerialNumber())) {
			
				return false;
			}
		} 
		
		return true;
	}
	public String ispis(String serial) {
		X509Certificate cert = getOne(serial);
		StringWriter streamWritter = new StringWriter();
		
		try {
			streamWritter.write("---------BEGIN----------\n");
			streamWritter.write(DatatypeConverter.printBase64Binary(cert.getEncoded()).replaceAll("(.{64})", "$1\n"));
			streamWritter.write("\n-----------END----------\n");
		} catch (CertificateEncodingException e) {
			e.printStackTrace();
		}
	   
		return streamWritter.toString();
	}

}
