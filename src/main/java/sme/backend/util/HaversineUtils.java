package sme.backend.util;

public class HaversineUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Tính khoảng cách giữa 2 điểm tọa độ (vĩ độ, kinh độ) dựa trên công thức Haversine.
     * Trả về khoảng cách tính bằng Kilometers.
     * 
     * Ví dụ (verify):
     * Hà Nội (21.0285, 105.8542) -> TP.HCM (10.8231, 106.6297)
     * Khoảng cách thực tế đường chim bay ≈ 1137 - 1150 km.
     * 
     * @param lat1 Vĩ độ điểm 1
     * @param lon1 Kinh độ điểm 1
     * @param lat2 Vĩ độ điểm 2
     * @param lon2 Kinh độ điểm 2
     * @return Khoảng cách (km)
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double a = Math.pow(Math.sin(dLat / 2), 2) +
                   Math.pow(Math.sin(dLon / 2), 2) *
                   Math.cos(lat1) *
                   Math.cos(lat2);
                   
        double c = 2 * Math.asin(Math.sqrt(a));
        
        return EARTH_RADIUS_KM * c;
    }
}
