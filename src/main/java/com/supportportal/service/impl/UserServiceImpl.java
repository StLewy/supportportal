package com.supportportal.service.impl;

import com.supportportal.domain.User;
import com.supportportal.domain.UserPrincipal;
import com.supportportal.enumeration.Role;
import com.supportportal.exception.domain.EmailExistException;
import com.supportportal.exception.domain.EmailNotFoundException;
import com.supportportal.exception.domain.UserNotFoundException;
import com.supportportal.exception.domain.UsernameExistException;
import com.supportportal.repository.UserRepository;
import com.supportportal.service.EmailService;
import com.supportportal.service.LoginAttemptService;
import com.supportportal.service.UserService;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;



import javax.mail.MessagingException;
import javax.transaction.Transactional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;


import static com.supportportal.constant.FileConstant.*;
import static com.supportportal.constant.UserImplConstant.*;
import static com.supportportal.enumeration.Role.ROLE_USER;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static jdk.internal.joptsimple.internal.Strings.EMPTY;
import static org.apache.commons.lang3.StringUtils.*;

@Service
@Transactional
@Qualifier("userDetailsService")
public class UserServiceImpl implements UserService, UserDetailsService {

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final EmailService emailService;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder, LoginAttemptService loginAttemptService, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.emailService = emailService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findUserByUsername(username);
        if (user == null) {
            LOGGER.error(NO_USER_FOUND_BY_USERNAME + username);
            throw new UsernameNotFoundException(NO_USER_FOUND_BY_USERNAME + username);
        } else {
            validateLoginAttempt(user);
            user.setLastLoginDateDisplay(user.getLastLoginDate());
            user.setLastLoginDate(new Date());
            userRepository.save(user);
            UserPrincipal userPrincipal = new UserPrincipal(user);
            LOGGER.info(RETURNING_FOUND_USER_BY_USERNAME + username);
            return userPrincipal;
        }
    }

    private void validateLoginAttempt(User user) {
        if (user.isNotLocked()) {
            user.setNotLocked(!loginAttemptService.hasExceededMaxAttempts(user.getUsername()));
        } else {
            loginAttemptService.evictUserFromLoginAttemptCache(user.getUsername());
        }
    }

    @Override
    public User register(String firstname, String lastname, String username, String email) throws UserNotFoundException, EmailExistException, UsernameExistException, MessagingException {
        validateNewUsernameAndEmail(StringUtils.EMPTY, username, email);
        User user = new User();
        user.setUserId(generateUserId());
        String password = generatePassword();
        user.setFirstName(firstname);
        user.setLastName(lastname);
        user.setUsername(username);
        user.setEmail(email);
        user.setJoinDate(new Date());
        user.setPassword(encodePassword(password));
        user.setActive(true);
        user.setNotLocked(true);
        user.setRoles(ROLE_USER.name());
        user.setAuthorities(ROLE_USER.getAuthorities());
        user.setProfileImageUrl(getTemporaryProfileImageURL(username));
        userRepository.save(user);
        LOGGER.info("New user password " + password);
        emailService.sendNewPasswordEmail(firstname, password, email);
        return user;
    }

    private String getTemporaryProfileImageURL(String username) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(DEFAULT_USER_IMAGE_PATH + username).toUriString();
    }

    private String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    private String generatePassword() {
        return RandomStringUtils.randomAlphanumeric(10);
    }

    private String generateUserId() {
        return RandomStringUtils.randomNumeric(10);
    }

    private User validateNewUsernameAndEmail(String currentUserName, String newUsername, String newEmail) throws UsernameExistException, EmailExistException, UserNotFoundException {
        User userByNewUsername = findUserByUsername(newUsername);
        User userByNewEmail = findUserByEmail(newEmail);
        if (isNotBlank(currentUserName)) {
            User currentUser = findUserByUsername(currentUserName);
            if (currentUser == null) {
                throw new UserNotFoundException(NO_USER_FOUND_BY_USERNAME + currentUserName);
            }
            if (userByNewUsername != null && !currentUser.getId().equals(userByNewUsername.getId())) {
                throw new UsernameExistException(USERNAME_ALREADY_EXIST);
            }
            if (userByNewEmail != null && !currentUser.getId().equals(userByNewEmail.getId())) {
                throw new EmailExistException(EMAIL_ALREADY_EXIST);
            }
            return currentUser;
        } else {
            if (userByNewUsername != null) {
                throw new UsernameExistException(USERNAME_ALREADY_EXIST);
            }
            if (userByNewEmail != null) {
                throw new EmailExistException(EMAIL_ALREADY_EXIST);
            }
            return null;
        }
    }

    @Override
    public User addNewUser(String firsName, String lastName, String username, String email, String role, boolean isNoLocked, boolean isActive, MultipartFile profileImage) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException {
        validateNewUsernameAndEmail(EMPTY, username, email);
        User user = new User();
        String password = generatePassword();
        user.setUserId(generateUserId());
        user.setFirstName(firsName);
        user.setLastName(lastName);
        user.setJoinDate(new Date());
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(encodePassword(password));
        user.setActive(isActive);
        user.setNotLocked(isNoLocked);
        user.setRoles(getRoleEnumName(role).name());
        user.setAuthorities(getRoleEnumName(role).getAuthorities());
        user.setProfileImageUrl(getTemporaryProfileImageURL(username));
        userRepository.save(user);
        saveProfileImage(user, profileImage);
        return user;
    }

    @Override
    public User updateUser(String currentUsername, String newFirsName, String newLastName, String newUsername, String newEmail, String role, boolean isNoLocked, boolean isActive, MultipartFile profileImage) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException {
        User currentUser = validateNewUsernameAndEmail(currentUsername, newUsername, newEmail);
        User user = new User();
        currentUser.setFirstName(newFirsName);
        currentUser.setLastName(newLastName);
        currentUser.setUsername(newUsername);
        currentUser.setEmail(newEmail);
        currentUser.setActive(isActive);
        currentUser.setNotLocked(isNoLocked);
        currentUser.setRoles(getRoleEnumName(role).name());
        currentUser.setAuthorities(getRoleEnumName(role).getAuthorities());
        userRepository.save(currentUser);
        saveProfileImage(currentUser, profileImage);
        return currentUser;
    }

    @Override
    public void deleteUser(Long id) {
        userRepository.deleteById(id);

    }

    @Override
    public void setPassword(String email) throws MessagingException, EmailNotFoundException {
        User user = userRepository.findUserByEmail(email);
        if (user == null){
            throw new EmailNotFoundException(NO_USER_FOUND_BY_EMAIL + email);
        } else {
            String password = generatePassword();
            user.setPassword(encodePassword(password));
            userRepository.save(user);
            emailService.sendNewPasswordEmail(user.getFirstName(),password, user.getEmail());
        }
    }

    @Override
    public User updateProfileImage(String username, MultipartFile profileImage) throws UserNotFoundException, EmailExistException, UsernameExistException, IOException {
         User user = validateNewUsernameAndEmail(username, null,null);
         saveProfileImage(user,profileImage);
        return user;
    }

    private void saveProfileImage(User user, MultipartFile profileImage) throws IOException {
        if (profileImage != null){
            Path userFolder = Paths.get(USER_FOLDER + user.getUsername()).toAbsolutePath().normalize();
            if (!Files.exists(userFolder)){
                Files.createDirectories(userFolder);
                LOGGER.info(DIRECTORY_CREATED + userFolder);
            }
            Files.deleteIfExists(Paths.get(userFolder + user.getUsername() + DOT + JPG_EXTTENSION));
            Files.copy(profileImage.getInputStream(), userFolder.resolve(user.getUsername() + DOT + JPG_EXTTENSION), REPLACE_EXISTING);
            user.setProfileImageUrl(setProfileImageURL(user.getUsername()));
            userRepository.save(user);
            LOGGER.info(FILE_SAVED_IN_FILE_SYSTEM + profileImage.getOriginalFilename());
        }
    }

    private String setProfileImageURL(String username) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(USER_IMAGE_PATH + username + FORWARD_SLASH
                + username + DOT + JPG_EXTTENSION).toUriString();

    }

    private Role getRoleEnumName(String role) {
        return Role.valueOf(role.toUpperCase());
    }

    @Override
    public List<User> getUsers() {
        return userRepository.findAll();
    }

    @Override
    public User findUserByUsername(String username) {
        return userRepository.findUserByUsername(username);
    }

    @Override
    public User findUserByEmail(String email) {
        return userRepository.findUserByEmail(email);
    }
}
