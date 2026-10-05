package com.skillmap.api.infrastructure.desktop;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PortSelectorTest {

    @Test
    void keepsPreferredPortWhenFree() {
        var env = env("8080");

        new PortSelector((address, port) -> true).selectPort(env);

        assertThat(env.getProperty("server.port", Integer.class)).isEqualTo(8080);
    }

    @Test
    void usesNextFreePortWhenPreferredIsTaken() {
        var env = env("8080");
        Set<Integer> taken = Set.of(8080, 8081);

        new PortSelector((address, port) -> !taken.contains(port)).selectPort(env);

        assertThat(env.getProperty("server.port", Integer.class)).isEqualTo(8082);
    }

    @Test
    void letsTheSystemChooseWhenNearbyPortsAreTaken() {
        var env = env("8080");

        new PortSelector((address, port) -> false).selectPort(env);

        assertThat(env.getProperty("server.port", Integer.class)).isZero();
    }

    @Test
    void defaultsTo8080WhenNotConfigured() {
        var env = new MockEnvironment();

        new PortSelector((address, port) -> port != 8080).selectPort(env);

        assertThat(env.getProperty("server.port", Integer.class)).isEqualTo(8081);
    }

    @Test
    void leavesRandomPortAlone() {
        var env = env("0");

        new PortSelector((address, port) -> false).selectPort(env);

        assertThat(env.getProperty("server.port", Integer.class)).isZero();
    }

    @Test
    void checksPortOnConfiguredAddress() {
        var env = env("8080");
        env.setProperty("server.address", "127.0.0.1");
        Set<InetAddress> probed = new HashSet<>();

        new PortSelector((address, port) -> probed.add(address)).selectPort(env);

        assertThat(probed).extracting(InetAddress::getHostAddress).containsExactly("127.0.0.1");
    }

    @Test
    void checksAllInterfacesWithoutConfiguredAddress() {
        var env = env("8080");
        Set<InetAddress> probed = new HashSet<>();

        new PortSelector((address, port) -> {
            probed.add(address);
            return true;
        }).selectPort(env);

        assertThat(probed).containsOnlyNulls();
    }

    private static MockEnvironment env(String port) {
        var env = new MockEnvironment();
        env.setProperty("server.port", port);
        return env;
    }
}
