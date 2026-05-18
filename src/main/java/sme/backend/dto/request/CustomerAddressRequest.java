package sme.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CustomerAddressRequest {
    @NotBlank
    private String receiverName;
    
    @NotBlank
    private String receiverPhone;
    
    @NotBlank
    private String provinceCity;
    
    @NotBlank
    private String district;
    
    private String ward;
    
    @NotBlank
    private String specificAddress;
    
    private Boolean isDefault;
}
