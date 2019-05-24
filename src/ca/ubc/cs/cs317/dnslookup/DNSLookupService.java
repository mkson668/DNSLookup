package ca.ubc.cs.cs317.dnslookup;

import java.io.Console;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;

public class DNSLookupService {

    private static final int DEFAULT_DNS_PORT = 53;
    private static final int MAX_INDIRECTION_LEVEL = 10;

    private static InetAddress rootServer;
    private static boolean verboseTracing = false;
    private static DatagramSocket socket;

    private static String mostRecentQueryMess = "";
    private static String mostRecentIP = "";

    //private static String nsurl = null;

    private static DNSCache cache = DNSCache.getInstance();

    private static Random random = new Random();

    private static int arrayCount = 0;

    /**
     * Main function, called when program is first invoked.
     *
     * @param args list of arguments specified in the command line.
     */
    public static void main(String[] args) {

        if (args.length != 1) {
            System.err.println("Invalid call. Usage:");
            System.err.println("\tjava -jar DNSLookupService.jar rootServer");
            System.err.println("where rootServer is the IP address (in dotted form) of the root DNS server to start the search at.");
            System.exit(1);
        }

        try {
            rootServer = InetAddress.getByName(args[0]);
            System.out.println("Root DNS server is: " + rootServer.getHostAddress());
        } catch (UnknownHostException e) {
            System.err.println("Invalid root server (" + e.getMessage() + ").");
            System.exit(1);
        }

        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(5000);
        } catch (SocketException ex) {
            ex.printStackTrace();
            System.exit(1);
        }

        Scanner in = new Scanner(System.in);
        Console console = System.console();
        do {
            // Use console if one is available, or standard input if not.
            String commandLine;
            if (console != null) {
                System.out.print("DNSLOOKUP> ");
                commandLine = console.readLine();
            } else
                try {
                    System.out.print("DNSLOOKUP> ");
                    commandLine = in.nextLine();
                } catch (NoSuchElementException ex) {
                    break;
                }
            // If reached end-of-file, leave
            if (commandLine == null) break;

            // Ignore leading/trailing spaces and anything beyond a comment character
            commandLine = commandLine.trim().split("#", 2)[0];

            // If no command shown, skip to next command
            if (commandLine.trim().isEmpty()) continue;

            String[] commandArgs = commandLine.split(" ");

            if (commandArgs[0].equalsIgnoreCase("quit") ||
                    commandArgs[0].equalsIgnoreCase("exit"))
                break;
            else if (commandArgs[0].equalsIgnoreCase("server")) {
                // SERVER: Change root nameserver
                if (commandArgs.length == 2) {
                    try {
                        rootServer = InetAddress.getByName(commandArgs[1]);
                        System.out.println("Root DNS server is now: " + rootServer.getHostAddress());
                    } catch (UnknownHostException e) {
                        System.out.println("Invalid root server (" + e.getMessage() + ").");
                        continue;
                    }
                } else {
                    System.out.println("Invalid call. Format:\n\tserver IP");
                    continue;
                }
            } else if (commandArgs[0].equalsIgnoreCase("trace")) {
                // TRACE: Turn trace setting on or off
                if (commandArgs.length == 2) {
                    if (commandArgs[1].equalsIgnoreCase("on"))
                        verboseTracing = true;
                    else if (commandArgs[1].equalsIgnoreCase("off"))
                        verboseTracing = false;
                    else {
                        System.err.println("Invalid call. Format:\n\ttrace on|off");
                        continue;
                    }
                    System.out.println("Verbose tracing is now: " + (verboseTracing ? "ON" : "OFF"));
                } else {
                    System.err.println("Invalid call. Format:\n\ttrace on|off");
                    continue;
                }
            } else if (commandArgs[0].equalsIgnoreCase("lookup") ||
                    commandArgs[0].equalsIgnoreCase("l")) {
                // LOOKUP: Find and print all results associated to a name.
                RecordType type;
                if (commandArgs.length == 2)
                    type = RecordType.A;
                else if (commandArgs.length == 3)
                    try {
                        type = RecordType.valueOf(commandArgs[2].toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        System.err.println("Invalid query type. Must be one of:\n\tA, AAAA, NS, MX, CNAME");
                        continue;
                    }
                else {
                    System.err.println("Invalid call. Format:\n\tlookup hostName [type]");
                    continue;
                }
                findAndPrintResults(commandArgs[1], type);
            } else if (commandArgs[0].equalsIgnoreCase("dump")) {
                // DUMP: Print all results still cached
                cache.forEachNode(DNSLookupService::printResults);
            } else {
                System.err.println("Invalid command. Valid commands are:");
                System.err.println("\tlookup fqdn [type]");
                System.err.println("\ttrace on|off");
                System.err.println("\tserver IP");
                System.err.println("\tdump");
                System.err.println("\tquit");
                continue;
            }

        } while (true);

        socket.close();
        System.out.println("Goodbye!");
    }

    /**
     * Finds all results for a host name and type and prints them on the standard output.
     *
     * @param hostName Fully qualified domain name of the host being searched.
     * @param type     Record type for search.
     */
    private static void findAndPrintResults(String hostName, RecordType type) {

        DNSNode node = new DNSNode(hostName, type);
        //if (verboseTracing) {
            //System.out.println("");    // added two blank lines before each query with verbose tracing
            //System.out.println("");
            mostRecentIP = rootServer.getHostAddress();
        //}
        printResults(node, getResults(node, 0));
    }

    /**
     * Finds all the result for a specific node.
     *
     * @param node             Host and record type to be used for search.
     * @param indirectionLevel Control to limit the number of recursive calls due to CNAME redirection.
     *                         The initial call should be made with 0 (zero), while recursive calls for
     *                         regarding CNAME results should increment this value by 1. Once this value
     *                         reaches MAX_INDIRECTION_LEVEL, the function prints an error message and
     *                         returns an empty set.
     * @return A set of resource records corresponding to the specific query requested.
     */
    private static Set<ResourceRecord> getResults(DNSNode node, int indirectionLevel) {

        if (indirectionLevel > MAX_INDIRECTION_LEVEL) {
            System.err.println("Maximum number of indirection levels reached.");
            return Collections.emptySet();
        }

        // TODO To be completed by the student

        Set<ResourceRecord> cachedResults = cache.getCachedResults(node);
        Set<ResourceRecord> cachedResultsCname =
                cache.getCachedResults(new DNSNode(node.getHostName(), RecordType.CNAME));

        Set<ResourceRecord> correctTypes = new HashSet<ResourceRecord>();    // ask if there's a simple match
        for (ResourceRecord rr : cachedResults) {
            if (rr.getType() == node.getType()) {
                correctTypes.add(rr);
            }
        }
        if (!correctTypes.isEmpty()) {
            return correctTypes;
        }


        ResourceRecord cName = null;          // ask if there's any CNAME that matches
        for (ResourceRecord rr : cachedResultsCname) {
            if (rr.getType() == RecordType.CNAME) {
                cName = rr;
                break;                                                      // get the CNAME and move on
            }
        }

        if (cName != null) {
            return getResults(new DNSNode(cName.getTextResult(), node.getType()),
                    indirectionLevel+1);
        } else {
            retrieveResultsFromServer(node, rootServer);
            Set<ResourceRecord> cachedResultsPQ = cache.getCachedResults(node);
            Set<ResourceRecord> cachedResultsPQCname =
                    cache.getCachedResults(new DNSNode(node.getHostName(), RecordType.CNAME));
            Set<ResourceRecord> correctTypesPQ = new HashSet<ResourceRecord>();    // ask if there's a simple match
            for (ResourceRecord rr : cachedResultsPQ) {
                if (rr.getType() == node.getType()) {
                    correctTypesPQ.add(rr);
                }
            }
            if (!correctTypesPQ.isEmpty()) {
                return correctTypesPQ;
            }

            ResourceRecord cNamePQ = null;          // ask if there's any CNAME that matches
            for (ResourceRecord rr : cachedResultsPQCname) {
                if (rr.getType() == RecordType.CNAME) {
                    cNamePQ = rr;
                    break;                                                      // get the CNAME and move on
                }
            }

            if (cNamePQ != null) {
                return getResults(new DNSNode(cNamePQ.getTextResult(), node.getType()),
                        indirectionLevel+1);
            }

            return Collections.emptySet();
        }
    }

    /**
     * Encodes a DNS Query
     *
     * @param node   Host name and record type to be used for the query.
     */
    private static byte[] encodeQuery(DNSNode node) {
        byte[] query = new byte[512];
        Random rand = new Random();
        int randomID = rand.nextInt(65535);
        if (verboseTracing) {
            RecordType rType = node.getType();
            String rTypeString = "";
            if (rType == RecordType.A) {
                rTypeString = "A";
            } else if (rType == RecordType.AAAA) {
                rTypeString = "AAAA";
            } else if (rType == RecordType.CNAME) {
                rTypeString = "CNAME";
            } else if (rType == RecordType.NS) {
                rTypeString = "NS";
            } else {
                rTypeString = Integer.toString(rType.getCode());
            }

            mostRecentQueryMess = "Query ID     " + randomID + " " + node.getHostName() + "  "
                    + rTypeString + " " + "-->" + " ";
        }
        // Query ID
        query[0] = (byte)(randomID >>> 8);
        query[1] = (byte) randomID;
        // Miscellaneous
        query[2] = 0;  
        query[3] = 0;
        // Question count
        query[4] = 0;  
        query[5] = 1;
        // Answer count
        query[6] = 0;  
        query[7] = 0;
        // Name server records
        query[8] = 0;  
        query[9] = 0;
        // Additional records
        query[10] = 0;  
        query[11] = 0;
        // Start of QName
        String hostName = node.getHostName();
        RecordType nodeType = node.getType();
        if (hostName.substring(hostName.length() - 1).equals(".")) {      // for robustness; ignores final period if resent
            hostName = hostName.substring(0, hostName.length() - 1);
        }
        String[] labels = hostName.split("\\.");  // period is a special regex character
        int[] labelLengths = new int[labels.length];
        int count = 0;
        for (String s : labels) {
            labelLengths[count] = s.length();
            count++;
        }
        arrayCount = 12;
        for (int i = 0; i < labels.length; i++) {   // outer label length loop
            int currLabelLength = labelLengths[i];
            query[arrayCount] = (byte) currLabelLength;
            arrayCount++;
            byte[] labelAsBytes;
            try {
                labelAsBytes = labels[i].getBytes("US-ASCII");
                int letterIndex = 0;
            do {                                     // inner label "letter" loop
                query[arrayCount] = labelAsBytes[letterIndex];
                arrayCount++;
                letterIndex++;
            } while (letterIndex < currLabelLength);
            } catch (Exception e) {
                // something went wrong
            }
        }
        // add 0 byte to signify end of url
        query[arrayCount++] = 0;
        query[arrayCount++] = (byte) (node.getType().getCode() >>> 8);
        query[arrayCount++] = (byte) node.getType().getCode();
        // type is internet
        query[arrayCount++] = 0;
        query[arrayCount++] = 1;
        byte[] queryTrimmed = new byte[arrayCount];
        for (int i = 0; i < arrayCount; i++) {
            queryTrimmed[i] = query[i];
        }
        return queryTrimmed;
    }

    /**
     * Retrieves DNS results from a specified DNS server. Queries are sent in iterative mode,
     * and the query is repeated with a new server if the provided one is non-authoritative.
     * Results are stored in the cache.
     *
     * @param node   Host name and record type to be used for the query.
     * @param server Address of the server to be used for the query.
     */
    private static void retrieveResultsFromServer(DNSNode node, InetAddress server) {

        // TODO To be completed by the student

            boolean authoritativeReached = false;

            boolean nsFound = false;          // used for proper formatting on NS queries, guards against extra query


            String cNameURL = "";

            while (!authoritativeReached) {

                byte[] query = encodeQuery(node);
                DatagramPacket queryPacket = new DatagramPacket(query, query.length, server, DEFAULT_DNS_PORT);
                if (verboseTracing) {
                    System.out.println("");    // added two blank lines before each query with verbose tracing
                    System.out.println("");
                    System.out.println(mostRecentQueryMess + server.getHostAddress());    // print out query for verbose tracing
                }
                try {
                    socket.send(queryPacket);
                } catch (IOException e) {
                    System.out.println("Error sending data packet");
                    return;
                }

                byte[] response = new byte[1024];
                DatagramPacket responsePacket = new DatagramPacket(response, 1024);
                try {
                    socket.receive(responsePacket);
                } catch (SocketTimeoutException e1) {      // socket timed out
                    try {
                        //socket.setSoTimeout(5000);
                        System.out.println(mostRecentQueryMess);
                        socket.send(queryPacket);
                        socket.receive(responsePacket);
                    } catch (Exception e) {
                        return;
                    }
                } catch (Exception e2) {                     // other errors
                    return;
                }

                // send off to server
                // get response back

                if ((response[3] & 0xf) != 0) {
                    // response is self reporting error
                    return;
                }

                int responseID = (response[0] << 8 & 0xff00) | response[1] & 0xFF;
                int answerAmt = (response[6] << 8 & 0xff00) | response[7] & 0xFF;
                int nsAmt = (response[8] << 8 & 0xff00) | response[9] & 0xFF;
                int addAmt = (response[10] << 8 & 0xff00) | response[11] & 0xFF;


                int rrOffset = 12;      // start at the question section


                // Bypass the question section of variable length
                try {
                    UrlAndPosition readResult = domainByteToString(response, rrOffset);
                    rrOffset = readResult.getPosAfterReading();
                    rrOffset += 4;  // skip qtype and qclass
                } catch (Exception e) {
                    // response is self reporting error
                    return;
                }


                boolean authServer = false;

                if ((response[2] & 0x4) == 4) {            // is this authoritative?
                    authServer = true;
                    authoritativeReached = true;           // this is the last iteration of the loop
                }

                if (verboseTracing) {
                    System.out.println("Response ID: " + responseID + " Authoritative" + " = " + authServer);
                }


                Set<ResourceRecord> answers = new HashSet<ResourceRecord>();

                if (verboseTracing) {
                    System.out.println("  Answers (" + answerAmt + ")");
                }

                for (int answersLeft = answerAmt; answersLeft > 0; answersLeft--) {
                    try {
                        UrlAndPosition readResult = domainByteToString(response, rrOffset);
                        rrOffset = readResult.getPosAfterReading();
                        String urlNameAnswer = readResult.getTextResult();
                        int ansTypeInt = (response[rrOffset] << 8 & 0xff00) | response[rrOffset + 1] & 0xFF;
                        RecordType ansType = RecordType.getByCode(ansTypeInt);
                        rrOffset += 4;  // skip qclass
                        byte ttlFirstByte = response[rrOffset++];
                        byte ttlSecondByte = response[rrOffset++];
                        byte ttlThirdByte = response[rrOffset++];
                        byte ttlFourthByte = response[rrOffset++];
                        long ttl = (ttlFirstByte & 0xFF) << 8;
                        ttl = (ttl + (ttlSecondByte & 0xFF)) << 8;
                        ttl = (ttl + (ttlThirdByte & 0xFF)) << 8;
                        ttl = (ttl + (ttlFourthByte & 0xFF));
                        int dataFieldLength = (response[rrOffset++] << 8) & 0xFF00;
                        dataFieldLength = dataFieldLength | (response[rrOffset++] & 0xFF);
                        if (ansType == RecordType.CNAME) {
                            // dealing with a CNAME
                            UrlAndPosition cNameURLandPos = domainByteToString(response, rrOffset);
                            cNameURL = cNameURLandPos.getTextResult();
                            rrOffset = cNameURLandPos.getPosAfterReading();
                            ResourceRecord answerRR = new ResourceRecord(urlNameAnswer, ansType, ttl, cNameURL);
                            cache.addResult(answerRR);
                            answers.add(answerRR);
                            verbosePrintResourceRecord(answerRR, ansType.getCode());
                        } else if (dataFieldLength == 4) {                  // IPV4 Answer
                            byte[] ipAddressBytes = new byte[4];
                            ipAddressBytes[0] = response[rrOffset++];
                            ipAddressBytes[1] = response[rrOffset++];
                            ipAddressBytes[2] = response[rrOffset++];
                            ipAddressBytes[3] = response[rrOffset++];
                            try {
                                InetAddress ipAddress = InetAddress.getByAddress(ipAddressBytes);
                                ResourceRecord answerRR = new ResourceRecord(urlNameAnswer, ansType, ttl, ipAddress);
                                // cache
                                cache.addResult(answerRR);
                                answers.add(answerRR);
                                verbosePrintResourceRecord(answerRR, ansType.getCode());
                            } catch (Exception e) {
                                return;
                            }
                        } else {                                  // IPV6 Answer
                            byte[] ipAddressBytes = new byte[16];
                            for (int i = 0; i < 16; i++) {
                                ipAddressBytes[i] = response[rrOffset++];
                            }
                            try {
                                InetAddress ipAddress = InetAddress.getByAddress(ipAddressBytes);
                                ResourceRecord answerRR = new ResourceRecord(urlNameAnswer, ansType, ttl, ipAddress);
                                // cache
                                cache.addResult(answerRR);
                                answers.add(answerRR);
                                verbosePrintResourceRecord(answerRR, 28);
                            } catch (Exception e) {
                                return;
                            }
                        }
                    } catch (UnsupportedEncodingException e) {
                        return;
                    }
                }


                Set<ResourceRecord> nameServers = new HashSet<ResourceRecord>();

                if (verboseTracing) {
                    System.out.println("  Nameservers (" + nsAmt + ")");
                }

                for (int nsLeft = nsAmt; nsLeft > 0; nsLeft--) {
                    try {
                        String domainOfRR = "";
                        UrlAndPosition rrName = domainByteToString(response, rrOffset);
                        rrOffset = rrName.getPosAfterReading();
                        domainOfRR = rrName.getTextResult();
                        int recValue = (response[rrOffset] << 8 & 0xff00) | response[rrOffset + 1] & 0xFF;
                        RecordType recType = RecordType.getByCode(recValue);
                        rrOffset += 4;                            // skip over class
                        byte ttlFirstByte = response[rrOffset++];
                        byte ttlSecondByte = response[rrOffset++];
                        byte ttlThirdByte = response[rrOffset++];
                        byte ttlFourthByte = response[rrOffset++];
                        long ttl = (ttlFirstByte & 0xFF) << 8;
                        ttl = (ttl + (ttlSecondByte & 0xFF)) << 8;
                        ttl = (ttl + (ttlThirdByte & 0xFF)) << 8;
                        ttl = (ttl + (ttlFourthByte & 0xFF));
                        int dataFieldLength = (response[rrOffset++] << 8) & 0xFF00;
                        dataFieldLength = dataFieldLength | (response[rrOffset++] & 0xFF);
                        UrlAndPosition nameServerName = domainByteToString(response, rrOffset);
                        String nsName = nameServerName.getTextResult();
                        rrOffset = nameServerName.getPosAfterReading();
                        ResourceRecord rr = new ResourceRecord(domainOfRR, recType, ttl, nsName);
                        if (domainOfRR.equals(node.getHostName())) {
                            nsFound = true;        // used for ns searches
                        }
                        cache.addResult(rr);
                        nameServers.add(rr);
                        verbosePrintResourceRecord(rr, 2);
                    } catch (UnsupportedEncodingException e) {
                        return;
                    }
                }

                Set<ResourceRecord> additionalInfo = new HashSet<ResourceRecord>();
                //ArrayList<ResourceRecord> rrIPs = new ArrayList<ResourceRecord>();

                if (verboseTracing) {
                    System.out.println("  Additional Information (" + addAmt + ")");
                }


                for (int addLeft = addAmt; addLeft > 0; addLeft--) {
                    try {
                        UrlAndPosition readResult = domainByteToString(response, rrOffset);
                        rrOffset = readResult.getPosAfterReading();
                        String urlNameAnswer = readResult.getTextResult();
                        int ansTypeInt = (response[rrOffset] << 8 & 0xff00) | response[rrOffset + 1] & 0xFF;
                        RecordType ansType = RecordType.getByCode(ansTypeInt);
                        rrOffset += 4;  // skip qclass
                        byte ttlFirstByte = response[rrOffset++];
                        byte ttlSecondByte = response[rrOffset++];
                        byte ttlThirdByte = response[rrOffset++];
                        byte ttlFourthByte = response[rrOffset++];
                        long ttl = (ttlFirstByte & 0xFF) << 8;
                        ttl = (ttl + (ttlSecondByte & 0xFF)) << 8;
                        ttl = (ttl + (ttlThirdByte & 0xFF)) << 8;
                        ttl = (ttl + (ttlFourthByte & 0xFF));
                        int dataFieldLength = (response[rrOffset++] << 8) & 0xFF00;
                        dataFieldLength = dataFieldLength | (response[rrOffset++] & 0xFF);
                        if (dataFieldLength == 16) {
                            byte[] ipAddressBytes = new byte[16];
                            for (int i = 0; i < 16; i++) {
                                ipAddressBytes[i] = response[rrOffset++];
                            }
                            try {
                                InetAddress ipAddress = InetAddress.getByAddress(ipAddressBytes);
                                ResourceRecord addRR = new ResourceRecord(urlNameAnswer, ansType, ttl, ipAddress);
                                // cache
                                cache.addResult(addRR);
                                additionalInfo.add(addRR);
                                //rrIPs.add(addRR);
                                verbosePrintResourceRecord(addRR, 28);
                            } catch (Exception e) {
                                return;
                            }
                        } else {
                            byte[] ipAddressBytes = new byte[4];
                            ipAddressBytes[0] = response[rrOffset++];
                            ipAddressBytes[1] = response[rrOffset++];
                            ipAddressBytes[2] = response[rrOffset++];
                            ipAddressBytes[3] = response[rrOffset++];
                            try {
                                InetAddress ipAddress = InetAddress.getByAddress(ipAddressBytes);
                                ResourceRecord addRR = new ResourceRecord(urlNameAnswer, ansType, ttl, ipAddress);
                                // cache
                                cache.addResult(addRR);
                                additionalInfo.add(addRR);
                                //rrIPs.add(addRR);
                                verbosePrintResourceRecord(addRR, 1);
                            } catch (Exception e) {
                                return;
                            }
                        }
                    } catch (UnsupportedEncodingException e) {
                        return;
                    }
                }

//                // check to see if we're just looking for an ns IP
//                if (nsurl != null) {
//
//                    return;
//                }

                // Update for next iteration of loop

                if (node.getType() == RecordType.NS) {
                    if (nsFound) {
                        return;                                // returns if NS search and servers found
                    }
                }

                boolean serverChanged = false;
                if (!authoritativeReached) {
                        for (ResourceRecord rr : nameServers) {
                            String nsURL = rr.getTextResult();
                            RecordType nodeRT = node.getType();
                            Set<ResourceRecord> nsIPs = cache.getCachedResults(new DNSNode(nsURL, RecordType.A)); // goes to any
                            for (ResourceRecord ns : nsIPs) {                                      // as long as it's
                                server = ns.getInetResult();     // the right type
                                serverChanged = true;
                                mostRecentIP = ns.getTextResult();
                            }
                        }

                        if (!serverChanged) {
                            for (ResourceRecord rr : nameServers) {
                                Set<ResourceRecord> nsResolve =
                                        getResults(new DNSNode(rr.getTextResult(), RecordType.A), 0);
                                if (!nsResolve.isEmpty()) {
                                    server = nsResolve.iterator().next().getInetResult();
                                    serverChanged = true;
                                    break;
                                }
                            }
                        }

                        if (!serverChanged) {
                            return;
                        }
                }
            }
    }




    private static UrlAndPosition domainByteToString (byte[] byteArray, int position) throws UnsupportedEncodingException {
        String domainName = "";
        byte currByte = byteArray[position++];
        //int currByteValue = (int) currByte;     // TODO see if there are problems with Endianess
        Byte currByteTransition = new Byte(currByte);
        int currByteValue = currByteTransition.intValue();
        int posAfterPointer = -1;          // -1 if there is no pointer, otherwise set to the point where we should
                                            // keep reading after the pointer
        while (true) {          // break when we see 0
            if (currByteValue < 0) {               // is it a pointer or not
                // pointer
                // position += 2;   // set to position of first byte after the pointer
                int pointerAdjustment = (currByteValue & 0x3F);  // chop off first two bits that aren't part of offset
                pointerAdjustment = pointerAdjustment << 8;
                currByte = byteArray[position++];
                int currByteUnsigned = ((int) currByte) & 0xFF;
                pointerAdjustment = pointerAdjustment + currByteUnsigned; // now the correct offset
                if (posAfterPointer == -1) {
                    posAfterPointer = position;             // set posAfterPointer only after seeing the first pointer
                }
                position = pointerAdjustment;
                currByte = byteArray[position++];
                currByteTransition = new Byte(currByte);
                currByteValue = currByteTransition.intValue();
            } else {
                // label length
                if (currByteValue == 0) {
                    break;
                }
                byte[] labelArray = new byte[currByteValue];
                for (int i = 0; i < currByteValue; i++) {
                    labelArray[i] = byteArray[position++];
                }
                String labelString = new String(labelArray, "US-ASCII");  // TODO Endinaness
                domainName = domainName + labelString + ".";
                currByte = byteArray[position++];
                currByteTransition = new Byte(currByte);
                currByteValue = currByteTransition.intValue();
            }
        }
        String urlName = "";
        if (domainName.length() > 1) {      // chop off last period
            urlName = domainName.substring(0, domainName.length() - 1);
        }
        if (posAfterPointer == -1) {
            return new UrlAndPosition(urlName, position);
        } else {
            return new UrlAndPosition(urlName, posAfterPointer);
        }
    }

    private static void verbosePrintResourceRecord(ResourceRecord record, int rtype) {
        if (verboseTracing)
            System.out.format("       %-30s %-10d %-4s %s\n", record.getHostName(),
                    record.getTTL(),
                    record.getType() == RecordType.OTHER ? rtype : record.getType(),
                    record.getTextResult());
    }

    /**
     * Prints the result of a DNS query.
     *
     * @param node    Host name and record type used for the query.
     * @param results Set of results to be printed for the node.
     */
    private static void printResults(DNSNode node, Set<ResourceRecord> results) {
        if (results.isEmpty())
            System.out.printf("%-30s %-5s %-8d %s\n", node.getHostName(),
                    node.getType(), -1, "0.0.0.0");
        for (ResourceRecord record : results) {
            System.out.printf("%-30s %-5s %-8d %s\n", node.getHostName(),
                    node.getType(), record.getTTL(), record.getTextResult());
        }
    }
}
