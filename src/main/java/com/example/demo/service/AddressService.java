package com.example.demo.service;

import com.example.demo.dto.ghn.GhnDistrict;
import com.example.demo.dto.ghn.GhnProvince;
import com.example.demo.dto.shop.AddressForm;
import com.example.demo.entity.UserAddress;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.UserAddressRepository;
import com.example.demo.repository.UserRepository;
import java.util.List;
import java.util.Locale;
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
     * (dữ liệu này đã có cache ở server) VÀ tên địa danh khách nhập phải khớp với tên của mã đó.
     * Nhờ vậy khách không thể chọn địa chỉ Hà Nội rồi sửa tên thành nơi khác để ăn phí ship rẻ hơn.
     * Mã sai/lệch tên ⇒ bỏ qua, địa chỉ vẫn dùng được và phí ship rơi về mức mặc định của shop.
     *
     * Khi mã hợp lệ, tên địa danh được ghi lại theo đúng tên GHN trả về để dữ liệu sạch và
     * khớp với vận đơn GHN sau này.
     */
    private void applyGhnCodes(UserAddress address, AddressForm form) {
        address.setProvinceId(null);
        address.setDistrictId(null);
        address.setWardCode(null);

        if (!ghnAddressService.isAvailable()
                || form.getProvinceId() == null || form.getDistrictId() == null) {
            return;
        }

        GhnProvince province = ghnAddressService.provinces().stream()
                .filter(item -> item.id() == form.getProvinceId())
                .findFirst()
                .orElse(null);
        if (province == null || !samePlaceName(province.name(), form.getProvinceName())) {
            return;
        }

        GhnDistrict district = ghnAddressService.districts(province.id()).stream()
                .filter(item -> item.id() == form.getDistrictId())
                .findFirst()
                .orElse(null);
        if (district == null || !samePlaceName(district.name(), form.getDistrictName())) {
            return;
        }

        address.setProvinceId(province.id());
        address.setProvinceName(province.name());
        address.setDistrictId(district.id());
        address.setDistrictName(district.name());

        String wardCode = trimToNull(form.getWardCode());
        if (wardCode == null) {
            return;
        }
        ghnAddressService.wards(district.id()).stream()
                .filter(ward -> ward.code().equals(wardCode) && samePlaceName(ward.name(), form.getWardName()))
                .findFirst()
                .ifPresent(ward -> {
                    address.setWardCode(ward.code());
                    address.setWardName(ward.name());
                });
    }

    /** So khớp tên địa danh khách nhập với tên GHN, bỏ qua tiền tố hành chính (TP., Quận, Phường…). */
    private boolean samePlaceName(String officialName, String typedName) {
        String official = normalizePlaceName(officialName);
        return !official.isEmpty() && official.equals(normalizePlaceName(typedName));
    }

    private String normalizePlaceName(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalized.replaceFirst(
                "^(tp\\.?|t\\.p\\.|thành phố|tỉnh|quận|huyện|thị xã|thị trấn|phường|xã)\\s+", "");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
