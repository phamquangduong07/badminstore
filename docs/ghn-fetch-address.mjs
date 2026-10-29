/**
 * Lấy TOÀN BỘ dữ liệu địa chỉ từ API GHN (Tỉnh → Quận/Huyện → Phường/Xã) để lưu sẵn cho app.
 *
 * Vì sao có script này: web bán hàng cần dropdown Tỉnh–Huyện–Xã đầy đủ 63 tỉnh/thành để khách chọn
 * địa chỉ giao hàng kể cả khi máy demo KHÔNG có mạng hoặc không có token GHN. Dữ liệu tải về được
 * chép vào `src/main/resources/ghn/{provinces,districts,wards}.json`; class
 * `GhnOfflineAddressData` đọc 3 file đó ở chế độ `app.ghn.mock=true`.
 * Mã tỉnh/huyện/xã ở đây là mã THẬT của GHN nên khi bật GHN thật (mock=false) địa chỉ đã lưu
 * vẫn tính được phí ship / tạo được vận đơn, không phải nhập lại.
 *
 * Cách chạy (Node 18+, cần token GHN của shop – KHÔNG commit token vào repo):
 *   PowerShell:  $env:GHN_TOKEN='<token>'; node docs/ghn-fetch-address.mjs src/main/resources/ghn
 *   bash:        GHN_TOKEN='<token>' node docs/ghn-fetch-address.mjs src/main/resources/ghn
 *
 * Kết quả mong đợi: 63 tỉnh/thành, ~722 quận/huyện, ~11.980 phường/xã.
 * (Vài quận đặc biệt GHN không trả phường nào – script in ra danh sách này, đó là dữ liệu của GHN.)
 */
import { writeFileSync, mkdirSync } from 'node:fs';

const BASE = process.env.GHN_BASE_URL || 'https://online-gateway.ghn.vn/shiip/public-api';
const TOKEN = process.env.GHN_TOKEN;
const OUT = process.argv[2] || 'src/main/resources/ghn';

if (!TOKEN) {
    console.error('Thiếu GHN_TOKEN. Ví dụ: $env:GHN_TOKEN=\'<token>\'; node docs/ghn-fetch-address.mjs');
    process.exit(1);
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

let requests = 0;
/** Gọi 1 endpoint master-data và trả về mảng `data`; tự thử lại khi mạng/GHN trục trặc. */
async function call(path) {
    for (let attempt = 1; attempt <= 6; attempt++) {
        requests++;
        try {
            const response = await fetch(BASE + path, { headers: { Token: TOKEN, Accept: 'application/json' } });
            const text = await response.text();
            let body = null;
            try {
                body = JSON.parse(text);
            } catch {
                body = null;
            }
            if (response.status === 401) {
                throw new Error('401 – token GHN không hợp lệ hoặc đã hết hạn');
            }
            if (response.ok && body && body.code === 200 && Array.isArray(body.data)) {
                return body.data;
            }
        } catch (error) {
            if (String(error.message).startsWith('401')) {
                throw error;
            }
        }
        await sleep(400 * attempt);
    }
    throw new Error('Gọi mãi không được: ' + path);
}

/** Chạy song song có giới hạn để không dồn dập API GHN. */
async function mapLimit(items, limit, worker) {
    const results = new Array(items.length);
    let index = 0;
    const runners = new Array(Math.min(limit, items.length)).fill(0).map(async () => {
        while (index < items.length) {
            const current = index++;
            results[current] = await worker(items[current]);
        }
    });
    await Promise.all(runners);
    return results;
}

const provinces = (await call('/master-data/province')).map((p) => ({
    ProvinceID: p.ProvinceID,
    ProvinceName: p.ProvinceName
}));
console.log('Tỉnh/thành:', provinces.length);

const districts = (
    await mapLimit(provinces, 4, async (province) => {
        const list = await call('/master-data/district?province_id=' + province.ProvinceID);
        return list.map((d) => ({ DistrictID: d.DistrictID, ProvinceID: d.ProvinceID, DistrictName: d.DistrictName }));
    })
).flat();
console.log('Quận/huyện:', districts.length);

const emptyDistricts = [];
const wards = (
    await mapLimit(districts, 6, async (district) => {
        try {
            const list = await call('/master-data/ward?district_id=' + district.DistrictID);
            return list.map((w) => ({ WardCode: String(w.WardCode), DistrictID: w.DistrictID, WardName: w.WardName }));
        } catch (error) {
            emptyDistricts.push(district.DistrictID + ' – ' + district.DistrictName + ': ' + error.message);
            return [];
        }
    })
).flat();
console.log('Phường/xã:', wards.length, '| số request:', requests);
if (emptyDistricts.length) {
    console.log('Quận không có phường nào (dữ liệu GHN trả về rỗng, ' + emptyDistricts.length + '):');
    emptyDistricts.forEach((line) => console.log('  ' + line));
}

mkdirSync(OUT, { recursive: true });
writeFileSync(OUT + '/provinces.json', JSON.stringify(provinces), 'utf8');
writeFileSync(OUT + '/districts.json', JSON.stringify(districts), 'utf8');
writeFileSync(OUT + '/wards.json', JSON.stringify(wards), 'utf8');
console.log('Đã ghi provinces.json / districts.json / wards.json vào', OUT);
