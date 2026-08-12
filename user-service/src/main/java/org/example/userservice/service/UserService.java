package org.example.userservice.service;

import org.example.userservice.dto.UserDto;
import org.example.userservice.dto.CreateUserRequest;
import org.example.userservice.dto.UserUpdateRequest;
import org.example.userservice.pojo.User;

public interface UserService {
    User getUserById(Long userId);
    User createUser(CreateUserRequest request);
    User updateUser(UserUpdateRequest request, Long userId);
    void deleteUser(Long userId);

    UserDto convertUserToDto(User user);
    User getAuthenticatedUser();
}
