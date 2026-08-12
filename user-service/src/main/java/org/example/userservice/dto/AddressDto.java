package org.example.userservice.dto;

import lombok.Data;

@Data
public class AddressDto {
    private Long id;
    private String name;
    private String locality;
    private String address;
    private String city;
    private String state;
    private String pinCode;
    private String mobile;
    private boolean isDefault;
}
