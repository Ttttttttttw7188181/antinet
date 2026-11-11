package com.antinet.vpn.shizuku;

import android.os.Bundle;

interface IShizukuUserService {
    Bundle startHotspot(int mtu, boolean ipv6, String dns);
    void stopHotspot();
}
