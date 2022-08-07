package net.com.pawelgorny.nodewatcher;

import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptPattern;
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

import java.io.*;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.util.*;

public class Main {

    private static String TARGET_ADDRESS = null;
    private static String LAST_BLOCK = "";
    private static Set<String> PROCESSED_TXID = new HashSet<>(0);

    private static HashMap<String, String> KNOWN = new HashMap<>(0);

    public static void main(String[] args) throws InterruptedException, MalformedURLException {
        if (args.length<3){
            System.out.println("Required parameters: cookiePath/credentials privkeyFile targetAddress");
        }
        BitcoinJSONRPCClient client = new BitcoinJSONRPCClient(getUrl(args[0], true));
        BitcoindRpcClient.AddressValidationResult validationResult = client.validateAddress(args[2]);
        if (!validationResult.isValid()){
            System.out.println("Address invalid! "+args[2]);
            System.exit(-3);
        }
        TARGET_ADDRESS = args[2];
        loadAddresses(args[1]);
        if (KNOWN.isEmpty()){
            System.out.println("Private keys not loaded! "+args[1]);
            System.exit(-2);
        }
        toFile("Program started");
        while (true){
            client = new BitcoinJSONRPCClient(getUrl(args[0], false));
            process(client);
            Thread.sleep(3000);
        }
    }



    private static void process(BitcoinJSONRPCClient client) throws InterruptedException {
        String block = null;
        //Long start = System.currentTimeMillis();
        do{
            try {
                block = client.getBestBlockHash();
            } catch (Exception e) {
                System.out.println((new Date())+e.getLocalizedMessage());
                toFile(e.getLocalizedMessage());
                Thread.sleep(10*1000);
            }
        }while(block == null);
        List<String> rawMemPoolTxids = client.getRawMemPool();
        if (!LAST_BLOCK.equalsIgnoreCase(block)){
            PROCESSED_TXID.retainAll(rawMemPoolTxids);
            System.out.println((new Date())+" New block: "+block);
        }
        rawMemPoolTxids.removeAll(PROCESSED_TXID);
        BitcoindRpcClient.RawTransaction rawTransaction;
        for (final String txid : rawMemPoolTxids){
            Set<BitcoindRpcClient.RawTransaction.Out> outs = new HashSet<>(0);
            try {
                String rawHex = client.getRawTransactionHex(txid);
                rawTransaction = client.decodeRawTransaction(rawHex);
            }catch (Exception e){
                System.out.println("Error1: "+e.getLocalizedMessage()+" "+txid);
                toFile("Error1: "+e.getLocalizedMessage()+" "+txid);
                continue;
            }
            boolean found=false;
            try{
                for (BitcoindRpcClient.RawTransaction.Out out : rawTransaction.vOut()){
                    List<String> vOutAddresses = out.scriptPubKey().addresses();
                    if (vOutAddresses == null || vOutAddresses.isEmpty()){
                        continue;
                    }
                    final String address = vOutAddresses.get(0);
                    if (KNOWN.containsKey(address)){
                        System.out.println("Found transaction "+address+" "+out.value()+" "+txid);
                        toFile("Found transaction "+address+" "+out.value()+" "+txid);
                        outs.add(out);
                        found = true;
                    }
                }
            }catch (Exception e){
                System.out.println("Error2: "+e.getLocalizedMessage()+" "+txid);
                toFile("Error2: "+e.getLocalizedMessage()+" "+txid);
            }
            PROCESSED_TXID.add(txid);
            if (!found){
                continue;
            }
                List<String> privateKeys = new ArrayList<>(outs.size());
                List<BitcoindRpcClient.TxInput> txInputs = new ArrayList<>(outs.size());
                BigDecimal amount = new BigDecimal(0);
                for (BitcoindRpcClient.RawTransaction.Out out : outs){
                    BitcoindRpcClient.TxInput input = out.toInput();
                    amount = amount.add(out.value());
                    privateKeys.add(KNOWN.get(out.scriptPubKey().addresses().get(0)));
                    txInputs.add(input);
                }
                List<BitcoindRpcClient.TxOutput> txOutputs = new ArrayList<>(1);
                txOutputs.add( new BitcoindRpcClient.BasicTxOutput(TARGET_ADDRESS, amount));
                String newTransactionHex = client.createRawTransaction(txInputs, txOutputs);
                amount = amount.subtract(new BigDecimal(newTransactionHex.length()*0.00000001)).setScale(8, BigDecimal.ROUND_HALF_EVEN);
                txOutputs.clear();
                txOutputs.add( new BitcoindRpcClient.BasicTxOutput(TARGET_ADDRESS, amount));
                newTransactionHex = client.createRawTransaction(txInputs, txOutputs);
                BitcoindRpcClient.SignedRawTransaction signedRawTransaction = client.signRawTransactionWithKey(newTransactionHex, privateKeys, txInputs, null);
                if (signedRawTransaction.errors()!=null && !signedRawTransaction.errors().isEmpty()){
                    System.out.println("Error3: "+txid+" "+signedRawTransaction.errors().get(0).error());
                    toFile("Error3: "+txid+" "+signedRawTransaction.errors().get(0).error());
                }else {
                    System.out.println(signedRawTransaction.hex());
                    toFile(signedRawTransaction.hex());
                }
                client.sendRawTransaction(signedRawTransaction.hex());
        }
        LAST_BLOCK = block;
        //System.out.println(System.currentTimeMillis()-start);
    }

    private static void loadAddresses(String privKeys) {
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(privKeys);
        } catch (FileNotFoundException e) {
            System.out.println("file not found "+privKeys);
            System.exit(-1);
        }
        System.out.println("processing keys");
        try {
            BufferedReader bufferReader = new BufferedReader(fileReader);
            String line;
            int x=0;
            while ((line = bufferReader.readLine()) != null) {
                ECKey ecKey;
                try {
                    ecKey = DumpedPrivateKey.fromBase58(MainNetParams.get(), line).getKey();
                }catch (Exception ee){
                    System.out.println("error with: "+line+": "+ee.getLocalizedMessage());
                    continue;
                }
                if (!ecKey.isCompressed()){
                    ecKey = ECKey.fromPrivate(ecKey.getPrivKey(), true);
                }
                String address =  LegacyAddress.fromKey(MainNetParams.get(), ecKey).toBase58();
                KNOWN.put(address, line);
                Script redeemScript = ScriptBuilder.createP2WPKHOutputScript(ecKey);
                Script script = ScriptBuilder.createP2SHOutputScript(redeemScript);
                byte[] scriptHash = ScriptPattern.extractHashFromP2SH(script);
                address =  LegacyAddress.fromScriptHash(MainNetParams.get(), scriptHash).toBase58();
                KNOWN.put(address, line);
                address = SegwitAddress.fromKey(MainNetParams.get(), ecKey).toBech32();
                KNOWN.put(address, line);
                ecKey = ecKey.decompress();
                address =  LegacyAddress.fromKey(MainNetParams.get(), ecKey).toBase58();
                KNOWN.put(address, ecKey.getPrivateKeyAsWiF(MainNetParams.get()));
                if (x%75==0){
                    System.out.println(x);
                }
                System.out.print(".");
                x++;
            }

            System.out.println("processing keys finished ["+x+"]");
        }catch (Exception e){
            System.out.println(e.getLocalizedMessage());
        }
    }

    private static String getUrl(String data, boolean print){
        if (data.startsWith("http")){
            if (print) {
                System.out.println("using direct http");
            }
            return data;
        }
        if (data.contains(".cookie")){
            File cookieFile = new File(data);
            if (!cookieFile.exists()){
                System.out.println("File does not exist! "+data);
                System.exit(-1);
            }
            try {
                String cookieFileContents = new String(Files.readAllBytes(cookieFile.toPath()));
                String[] temp = cookieFileContents.split(":");
                String user = temp[0];
                String password = temp[1];
                return "http://"+user+":"+password+"@127.0.0.1:8332/";
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
        System.exit(-2);
        return null;
    }

    private static void toFile(String data) {
        try {
            FileWriter fileWriter = new FileWriter("log.txt", true);
            fileWriter.write("["+new Date()+"] "+data);
            fileWriter.write("\r\n");
            fileWriter.close();
        } catch (IOException e) {
            System.out.println("Cannot write to file: " + e.getLocalizedMessage());
        }
    }
}
