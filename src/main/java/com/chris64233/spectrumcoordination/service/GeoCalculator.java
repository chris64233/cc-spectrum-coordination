package com.chris64233.spectrumcoordination.service;

/**
 * 地理与干扰判定工具（WGS-84 经纬度，米制距离）。
 *
 * <p>干扰区域模型：每个台站在自身覆盖半径之外，按规则版本获得一段同频干扰余量
 * （固定余量 + 功率相关余量）。两个使用区域在"扩展覆盖圆"相交时视为干扰区域重叠：
 *
 * <pre>
 * 扩展半径 A = 使用半径 A + 余量(功率 A)
 * 扩展半径 B = 使用半径 B + 余量(功率 B)
 * 干扰重叠   ⇔  圆心距 &lt; 扩展半径 A + 扩展半径 B
 * </pre>
 */
public final class GeoCalculator {

    /** WGS-84 平均地球半径（米）。 */
    static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoCalculator() {
    }

    /** Haversine 大圆距离（米）。 */
    public static double distanceMeters(double lat1Deg, double lon1Deg,
                                        double lat2Deg, double lon2Deg) {
        double lat1 = Math.toRadians(lat1Deg);
        double lat2 = Math.toRadians(lat2Deg);
        double dLat = Math.toRadians(lat2Deg - lat1Deg);
        double dLon = Math.toRadians(lon2Deg - lon1Deg);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /**
     * 判断两个（可能扩展的）覆盖圆是否相交。
     *
     * @return 重叠深度（米）；不重叠返回负数（相切/分离）
     */
    public static double circleOverlapMeters(double lat1, double lon1, double radius1,
                                             double lat2, double lon2, double radius2) {
        return radius1 + radius2 - distanceMeters(lat1, lon1, lat2, lon2);
    }
}
