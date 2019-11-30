import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RoutingTable {
    private ArrayList<RoutingRow> table = new ArrayList<>();
    private static RoutingTable instance;

    public static RoutingTable getInstance() {
        if(instance == null) {
            instance = new RoutingTable();
        }
        return instance;
    }

    public List<String[]> GetTblRows(){

        List<String[]> tblRows = new ArrayList<>();

        for (int i = 0; i < table.size(); i++){
            String[] row = new String[6];

            row[0] = table.get(i).getDestinationStr();
            row[1] = table.get(i).getNetMaskStr();
            row[2] = table.get(i).getFlags()[1] ? table.get(i).getGatewayStr() : "*";
            row[3] = table.get(i).getFlagsStr();
            row[4] = table.get(i).getInterfaceName();
            row[5] = table.get(i).getMetricStr();

            tblRows.add(row);
        }
        return tblRows;
    }

    public void remove(byte[] destination){
        RoutingRow routingRow;
        for (int i = 0; i < table.size(); i++) {
            routingRow = table.get(i);
            if(Arrays.equals(destination, routingRow.destination)){
                table.remove(i);
                break;
            }
        }
    }

    public RoutingRow getRoutingTableRow(byte[] destination, int netmask, byte[] gateway, boolean[] flags, String interfaceName, int metric){
        return new RoutingRow(destination, netmask, gateway, flags, interfaceName, metric);
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

        public String getDestinationStr(){

            int dest_0 = destination[0] < 0 ? destination[0] + 256 : destination[0];
            int dest_1 = destination[1] < 0 ? destination[1] + 256 : destination[1];
            int dest_2 = destination[2] < 0 ? destination[2] + 256 : destination[2];
            int dest_3 = destination[3] < 0 ? destination[3] + 256 : destination[3];

            return dest_0 + "." + dest_1 + "." + dest_2 + "." + dest_3;
        }

        public String getNetMaskStr(){

            String[] res = new String[4];
            Arrays.fill(res, "0");

            int loopCnt = netmask / 8;
            int remain = netmask % 8;

            for (int i = 0; i < loopCnt; i++){
                res[i] = Integer.toString(255);
            }

            res[loopCnt] = Integer.toString((255 - (int) Math.pow(2, 8 - remain) + 1));

            return res[0] + "." + res[1] + "." + res[2] + "." + res[3];
        }

        public String getGatewayStr(){

            int gateway_0 = gateway[0] < 0 ? gateway[0] + 256 : gateway[0];
            int gateway_1 = gateway[1] < 0 ? gateway[1] + 256 : gateway[1];
            int gateway_2 = gateway[2] < 0 ? gateway[2] + 256 : gateway[2];
            int gateway_3 = gateway[3] < 0 ? gateway[3] + 256 : gateway[3];

            return gateway_0 + "." + gateway_1 + "." + gateway_2 + "." + gateway_3;
        }

        public String getFlagsStr(){
            String res = "";
            if(flags[0] == true) res += "U";
            if(flags[1] == true) res += "G";
            if(flags[2] == true) res += "H";
            return res;
        }

        public String getMetricStr(){
            return Integer.toString(metric);
        }

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

    }

    public RoutingRow Route(byte[] destIPAddr){

        if (table.size() < 1) return null;

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
                return row;
            }
        }

        // 못 찾으면 default Row 반환
        return table.get(table.size() - 1);
    }
}
