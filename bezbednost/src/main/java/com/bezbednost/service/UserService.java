package com.bezbednost.service;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bezbednost.model.User;
import com.bezbednost.model.UserDTO;
import com.bezbednost.model.UserType;
import com.bezbednost.repository.UserRepository;

@Service
public class UserService {
	@Autowired
	private UserRepository userRepository;
	
	 
	public void registerUser(UserDTO userDTO) {
		byte[] salt = KeyGenerator.generateSalt();
		byte[] hash = KeyGenerator.hashPassword(userDTO.getPassword(), salt);
		
		User user = new User(userDTO.getUsername(), UserType.ADMIN, salt, hash);
		
		try {	
			
			userRepository.save(user);
		} catch(Exception e) { 
			e.printStackTrace(); 
			throw new RuntimeException("Username postoji!"); 
		}
	}
	
	public User getUser(UserDTO userDTO) {
		User user = userRepository.findByUsername(userDTO.getUsername());
		
		
		if (user == null) {
			throw new RuntimeException("Pogresan username ili lozinka!");
		} 
		
		byte[] passHash = KeyGenerator.hashPassword(userDTO.getPassword(), user.getPasswordSalt());
		
	    if (!Arrays.equals(passHash, user.getPasswordHash())) {
			throw new RuntimeException("Pogresan username ili lozinka!");
	    }
		
		return user;
	}

}
