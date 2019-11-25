import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class RoutingTable {
    private ArrayList<RoutingRow> table = new ArrayList<>();
    private static RoutingTable instance;

    public static RoutingTable getInstance() {
        if(instance == null) {
            instance = new RoutingTable();
        }
        return instance;
    }

    public void add(RoutingRow row) {
        table.add(row);
        Collections.sort(table);
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
            if(this.getNetmask() > row.getNetmask())
                return -1;
            else if(this.getNetmask() < row.getNetmask())
                return 1;
            else
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

        public byte[] Route(byte[] destIPAddr){

           for (RoutingRow row : table) {
               byte[] maskResult = new byte[4];
               byte[] subnetMask = new byte[4];

               int loopCnt = row.netmask / 8;
               int remain = row.netmask % 8;

               for (int i = 0; i < loopCnt; i++) {
                   // 255로 채움
                   subnetMask[i] = -1;
               }

               // 8로 떨어지지 않는 값은 255에서 뺀 값으로 계산.
               // int로 고쳐 사용할 땐 256을 더해 사용하면 됨.
               if(remain != 0) subnetMask[loopCnt] = (byte) ((255 - Math.pow(2, 8 - remain) + 1) - 256);

               for (int i = 0; i < 4; i++) {
                   maskResult[i] = (byte) (destIPAddr[i] & subnetMask[i]);
               }

               // 라우팅 될 row를 찾음
               if(Arrays.equals(maskResult, row.destination)) {
                   return row.destination;
               }
           }

           // 못 찾으면 default Row 반환
           return table.get(table.size() - 1).destination;
        }

    }
}
