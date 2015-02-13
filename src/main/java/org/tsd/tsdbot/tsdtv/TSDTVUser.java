package org.tsd.tsdbot.tsdtv;

import org.jibble.pircbot.User;

import java.net.InetAddress;

/**
* Created by Joe on 2/4/2015.
*/
public abstract class TSDTVUser {

    public abstract boolean isOp();
    public abstract String getId();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TSDTVUser tsdtvUser = (TSDTVUser) o;

        if (!getId().equals(tsdtvUser.getId())) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }
}
