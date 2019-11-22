package arp;

import java.util.ArrayList;

public class RoutingTable {
    public ArrayList<RoutingRow> table = new ArrayList<>();
    private static RoutingTable instance;

    public static RoutingTable getInstance() {
        if(instance == null) {
            instance = new RoutingTable();
        }
        return instance;
    }

    private RoutingTable() {}

    public class RoutingRow implements Comparable<RoutingRow>{
        private byte[] destination;
        private int netmask;
        private byte[] gateway;
        private boolean[] flags; //flags[0] = Up, flags[1] = Gateway, flags[2] = Host
        private String interfaceName;
        int metric;

        public RoutingRow(byte[] destination, int netmask, byte[] gateway, boolean[] flags, String interfaceName, int metric) {
            setDestination(destination);
            setNetmask(netmask);
            setGateway(gateway);
            setFlags(flags);
            setInterfaceName(interfaceName);
            setMetric(metric);
        }

//        for sorting
        @Override
        public int compareTo(RoutingRow row) {
            return 0;
        }

//        getter & setter
        public void setInterfaceName(String interfaceName) {
            this.interfaceName = interfaceName;
        }

        public void setDestination(byte[] destination) {
            this.destination = destination;
        }

        public void setFlags(boolean[] flags) {
            this.flags = flags;
        }

        public void setGateway(byte[] gateway) {
            this.gateway = gateway;
        }

        public void setMetric(int metric) {
            this.metric = metric;
        }

        public void setNetmask(int netmask) {
            this.netmask = netmask;
        }

        public boolean[] getFlags() {
            return flags;
        }

        public byte[] getDestination() {
            return destination;
        }

        public byte[] getGateway() {
            return gateway;
        }

        public int getNetmask() {
            return netmask;
        }

        public int getMetric() {
            return metric;
        }

        public String getInterfaceName() {
            return interfaceName;
        }
    }
}
