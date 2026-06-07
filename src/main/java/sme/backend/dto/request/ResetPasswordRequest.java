package sme.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Email là bắt buộc")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Mã OTP là bắt buộc")
    private String otp;

    @NotBlank(message = "Mật khẩu mới là bắt buộc")
    @Size(min = 8, message = "Mật khẩu mới phải dài ít nhất 8 ký tự")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", message = "Mật khẩu mới phải chứa ít nhất 1 chữ hoa, 1 chữ thường và 1 số")
    private String newPassword;
}
