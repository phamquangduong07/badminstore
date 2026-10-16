package com.example.demo.service;

import com.example.demo.dto.shop.AddressForm;
import com.example.demo.entity.UserAddress;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.UserAddressRepository;
import com.example.demo.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sổ địa chỉ giao hàng của khách: thêm, đặt mặc định, xoá mềm.
 *
 * Lưu ý ràng buộc DB: UX_user_addresses_default – mỗi khách tối đa 1 địa chỉ mặc định,
 * nên mọi thao tác đổi mặc định đều theo thứ tự "bỏ cờ cũ TRƯỚC, đặt cờ mới SAU" trong 1 transaction.
 */
@Service
@RequiredArgsConstructor
public class AddressService {

    /** Giới hạn số địa chỉ lưu được của mỗi khách (tránh spam dữ liệu rác). */
    public static final int MAX_ADDRESSES = 5;

    private final UserAddressRepository addressRepository;
    private final UserRepository userRepository;
    private final GhnAddressService ghnAddressService;

    @Transactional(readOnly = true)
    public List<UserAddress> list(Integer userId) {
        return addressRepository.findByUserIdAndDeletedFalseOrderByDefaultAddressDescCreatedAtDesc(userId);
    }

    /** Địa chỉ mặc định (nếu có) – dùng để tích sẵn ở bước thanh toán. */
    @Transactional(readOnly = true)
    public Optional<UserAddress> defaultAddress(Integer userId) {
        return addressRepository.findFirstByUserIdAndDefaultAddressTrueAndDeletedFalse(userId);
    }

    /** Địa chỉ phải thuộc đúng khách đang đăng nhập, nếu không thì từ chối. */
    @Transactional(readOnly = true)
    public UserAddress requireOwned(Integer userId, Integer addressId) {
        if (addressId == null) {
            throw new BusinessException("Vui lòng chọn địa chỉ nhận hàng.");
        }
        return addressRepository.findByIdAndUserIdAndDeletedFalse(addressId, userId)
                .orElseThrow(() -> new BusinessException(
                        "Địa chỉ nhận hàng không hợp lệ. Bạn chọn lại hoặc thêm địa chỉ mới giúp mình nhé."));
    }

    @Transactional
    public UserAddress create(Integer userId, AddressForm form) {
        List<UserAddress> existing = list(userId);
        if (existing.size() >= MAX_ADDRESSES) {
            throw new BusinessException("Bạn chỉ lưu được tối đa " + MAX_ADDRESSES
                    + " địa chỉ. Hãy xoá bớt địa chỉ không dùng trước khi thêm mới.");
        }

        UserAddress address = new UserAddress();
        address.setUser(userRepository.getReferenceById(userId));
        apply(address, form);

        // Địa chỉ đầu tiên luôn là mặc định; các địa chỉ sau theo lựa chọn của khách.
        boolean makeDefault = form.isDefaultAddress() || existing.isEmpty();
        address.setDefaultAddress(false);
        UserAddress saved = addressRepository.save(address);

        if (makeDefault) {
            addressRepository.clearDefaultAddress(userId);
            saved.setDefaultAddress(true);
        }
        return saved;
    }

    @Transactional
    public void setDefault(Integer userId, Integer addressId) {
        UserAddress address = requireOwned(userId, addressId);
        if (address.isDefaultAddress()) {
            return;
        }
        addressRepository.clearDefaultAddress(userId);
        address.setDefaultAddress(true);
    }

    /** Xoá mềm; nếu vừa xoá địa chỉ mặc định thì lấy địa chỉ mới nhất còn lại làm mặc định. */
    @Transactional
    public void delete(Integer userId, Integer addressId) {
        UserAddress address = requireOwned(userId, addressId);
        boolean wasDefault = address.isDefaultAddress();

        address.setDefaultAddress(false);
        address.setDeleted(true);
        addressRepository.saveAndFlush(address);

        if (wasDefault) {
            list(userId).stream().findFirst().ifPresent(next -> next.setDefaultAddress(true));
        }
    }

    private void apply(UserAddress address, AddressForm form) {
        address.setReceiverName(form.getReceiverName().trim());
        address.setReceiverPhone(form.getReceiverPhone().trim());
        address.setProvinceName(trimToNull(form.getProvinceName()));
        address.setDistrictName(trimToNull(form.getDistrictName()));
        address.setWardName(trimToNull(form.getWardName()));
        address.setAddressDetail(form.getAddressDetail().trim());
        applyGhnCodes(address, form);
    }

    /**
     * Lưu mã Tỉnh–Huyện–Xã của GHN kèm theo địa chỉ (nếu khách chọn từ dropdown).
     *
     * Khách gửi mã từ trình duyệt nên KHÔNG tin ngay: mã phải tồn tại thật trong dữ liệu GHN
     * mới được lưu (dữ liệu này đã có cache ở server). Mã sai/lạ ⇒ bỏ qua, địa chỉ vẫn dùng được
     * và phí ship rơi về mức phí mặc định của shop.
     */
    private void applyGhnCodes(UserAddress address, AddressForm form) {
        address.setProvinceId(null);
        address.setDistrictId(null);
        address.setWardCode(null);

        if (!ghnAddressService.isAvailable()
                || form.getProvinceId() == null || form.getDistrictId() == null) {
            return;
        }
        boolean provinceOk = ghnAddressService.provinces().stream()
                .anyMatch(province -> province.id() == form.getProvinceId());
        boolean districtOk = ghnAddressService.districts(form.getProvinceId()).stream()
                .anyMatch(district -> district.id() == form.getDistrictId());
        if (!provinceOk || !districtOk) {
            return;
        }
        address.setProvinceId(form.getProvinceId());
        address.setDistrictId(form.getDistrictId());

        String wardCode = trimToNull(form.getWardCode());
        if (wardCode != null && ghnAddressService.wards(form.getDistrictId()).stream()
                .anyMatch(ward -> ward.code().equals(wardCode))) {
            address.setWardCode(wardCode);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
