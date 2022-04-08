package com.supportportal.service;

import com.supportportal.domain.User;
import com.supportportal.exception.domain.EmailExistException;
import com.supportportal.exception.domain.EmailNotFoundException;
import com.supportportal.exception.domain.UserNotFoundException;
import com.supportportal.exception.domain.UsernameExistException;
import org.springframework.web.multipart.MultipartFile;


import javax.mail.MessagingException;
import java.io.IOException;
import java.util.List;

public interface UserService {
 User register(String firstname, String lastname, String username, String email) throws UserNotFoundException, EmailExistException, UsernameExistException, MessagingException;

 List<User> getUsers();

 User findUserByUsername(String username);

 User findUserByEmail(String email);

 User addNewUser(String firsName, String lastName, String username, String email, String role, boolean isNoLocked, boolean isActive, MultipartFile profileImage) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException;

 User updateUser(String currentUsername,String newFirsName, String newLastName, String newUsername, String newEmail, String role, boolean isNoLocked, boolean isActive, MultipartFile profileImage) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException;

 void deleteUser(Long id);

 void setPassword(String email) throws MessagingException, EmailNotFoundException;

 User updateProfileImage(String username, MultipartFile profileImage) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException;
}
