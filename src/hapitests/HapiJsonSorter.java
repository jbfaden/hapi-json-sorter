
package hapitests;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * sort HAPI responses into a canonical order
 * @author jbf
 */
public class HapiJsonSorter {
    
    private static final Logger logger= Logger.getLogger("HapiSorter");
    
    enum MapType {
        ABOUT,
        INFO,
        PARAMETER,
        PARAMETER_BINS,        
        HAPI, 
        REQUEST_STATUS,
        HAPI_STATUS,
        SCHEMA_NODE,
        SCHEMA_PROPERTY,
        SCHEMA_DEFINITIONS,
        MAP
    }
    
    enum FileType {
        HAPI,
        HAPI_SCHEMA,
        HAPI_COMBINED_SCHEMA // Bob combines each of the schemas into one JSON file.
    }
    
    private static MapType identifyMapType( FileType type, String name, Map m) {
        if ( type==FileType.HAPI_SCHEMA ) {
            if ( name.equals("anyOf") ) {
                return MapType.SCHEMA_PROPERTY;
            } else if ( name.equals("properties") ) {
                // it's probably a parameter, but let the code below identify object type
                logger.fine("properties");
            } else if ( name.equals("definitions") && m.containsKey("HAPI") ) {
                return MapType.SCHEMA_DEFINITIONS;
            } else {
                return MapType.SCHEMA_NODE;
            }
        } else if ( type==FileType.HAPI_COMBINED_SCHEMA ) {
            return MapType.MAP;
        }
        if ( m.containsKey("HAPI") ) {
            if ( m.containsKey("parameters") ) {
                return MapType.INFO;
            }
        }
        if ( m.containsKey("name") && m.containsKey("type") ) {
            return MapType.PARAMETER;
        } 
        if ( m.containsKey("name") && ( m.containsKey("centers") || m.containsKey("ranges") ) ) {
            return MapType.PARAMETER_BINS;
        }
        if ( m.containsKey("description") && m.containsKey("type") ) {
            return MapType.SCHEMA_PROPERTY;
        }
        if ( m.containsKey("code") && m.containsKey("message") ) {
            return MapType.REQUEST_STATUS;   
        }
        if ( m.containsKey("HAPI") && m.containsKey("status") ) {
            return MapType.HAPI_STATUS;
        }
        return MapType.MAP;
    }
    
    /**
     * return a list of keys that should appear before others, in the order
     * that they should appear.
     * @param mapType
     * @return 
     */
    private static List<String> sortForType( MapType mapType ) {
        if ( mapType==MapType.PARAMETER ) {
            return Arrays.asList( "name", "type", "length", "size", "units",
                "coordinateSystemName", "vectorComponents", "fill", 
                "description", "label", "bins" );
        } else if ( mapType==MapType.PARAMETER_BINS ) {
            return Arrays.asList( "name", "centers", "ranges", 
                "units", "label", "description" );
        } else if ( mapType==MapType.INFO ) {
            return Arrays.asList( "$schema", "HAPI", "status", "format", "parameters", 
                "startDate", "stopDate", "timeStampLocation", "cadence", 
                "sampleStartDate", "sampleStopDate", "maxRequestDuration",
                "description", "unitsSchema", "coordinateSystemSchema",
                "resourceURL", "resourceID", "creationDate", "citation", 
                "modificationDate", "contact", "contactID", "additionalMetadata" );
        } else if ( mapType==MapType.SCHEMA_PROPERTY ) { // these are right underneath the HAPI node (HAPI, status).
            return Arrays.asList( "description", "id", "title",  "pattern", 
                "type", "enum", "required", "items",
                "patternProperties", "additionalProperties",
                "minItems", "additionalItems", "uniqueItems" );
        } else if ( mapType==MapType.SCHEMA_NODE ) {
            return Arrays.asList( "description","id", "title", "pattern", 
                "type", "required", "items",
                "patternProperties", "additionalProperties",
                "minItems", "additionalItems", "uniqueItems" );        
        } else if ( mapType==MapType.SCHEMA_DEFINITIONS ) {
            return Arrays.asList( "$schema", "HAPI", "HAPIDateTime", 
                "HAPIStatus", "UnitsAndLabel", "Ref", "about", "capabilities", 
                "catalog","info" );
        } else if ( mapType==MapType.REQUEST_STATUS ) {
            return Arrays.asList( "code", "message" );
        } else if ( mapType==MapType.HAPI_STATUS ) {
            return Arrays.asList( "HAPI", "status" );
        } else {
            return Collections.emptyList();
        }
    }
    
    /**
     * sort the keys for the type of map.  For example a parameter should have
     * as its first key the key "name".  This will return the same list
     * to indicate that no sorting needs to be done.
     * @param mapType
     * @param keys
     * @return 
     */
    private static List<String> sortKeysForType( MapType mapType, List<String> keys ) {
        
        List<String> first= sortForType(mapType);
        if ( first.size()>0 ) {
            keys= new ArrayList<>(keys);
            ArrayList<String> result= new ArrayList();
            for ( String k : first ) {
                int i= keys.indexOf(k);
                if ( i>-1 ) {
                    String s= keys.remove(i);
                    result.add(s);
                }
            }
            result.addAll(keys);
            return result;
        } else {
            return keys;
        }
    }
        
    private static Map sortMap( FileType type, String name, Map m, int depth ) {

        MapType mapType= identifyMapType( type, name, m );
        if ( type==FileType.HAPI_COMBINED_SCHEMA ) {
            type= FileType.HAPI_SCHEMA;
        }
        if ( mapType==MapType.MAP ) {
            // why did we fail to ID this map?
            mapType= identifyMapType( type, name, m );
        }
        List keys= new ArrayList(m.keySet());
        for ( int i=0; i<keys.size(); i++ ) {
            String k= (String)keys.get(i);
            Object o= m.get(k);
            if ( o instanceof Map ) {
                //MapType childMapType= identifyMapType( type, k, (Map)o );
                //m.put(k+"::"+childMapType, sortMap( type, k, (Map)o, depth+1 ) );
                m.put(k, sortMap( type, k, (Map)o, depth+1 ) );
            } else if ( o instanceof List ) {
                List list= (List)o;
                if ( list.size()>0 && k.equals("anyOf") ) {
                    Object o2= list.get(0);
                    if ( o2 instanceof Map ) {
                        mapType= identifyMapType( type, name, m );
                        //System.err.println(">>> anyOf type is "+mapType + " file type is "+type );
                    }
                }
                for ( int l= 0; l<list.size(); l++ ) {
                    Object o2= list.get(l);
                    if ( o2 instanceof Map ) {
                        list.set(l, sortMap( type, "element of "+k, (Map)o2, depth+1 ) );
                    }
                }
            }
        }
        
        if ( mapType==MapType.PARAMETER ) {
            logger.fine("parameter");
        } else if ( mapType==MapType.INFO ) {
            logger.fine("info");
        } else if ( mapType==MapType.SCHEMA_NODE ) {
            logger.fine("schema_node");
        }
        ArrayList<String> l= new ArrayList<>(m.keySet());
        List<String> lsorted= sortKeysForType( mapType, l );

        if ( lsorted!=l ) {
            LinkedHashMap result= new LinkedHashMap();
            for ( String k: lsorted ) {
                if ( m.get(k) instanceof List && ((List)m.get(k)).size()>0 ) {
                    Object o= ((List)m.get(k)).get(0);
                    if ( o instanceof Map ) {
                        //MapType childMapType= identifyMapType( type, k, (Map)o );
                        //result.put( k+"::"+childMapType, m.get(k) );
                        result.put( k, m.get(k) );
                        continue;
                    }
                }
                result.put( k, m.get(k) );
            }
            return result;            
        } else {
            System.err.println("no sorting applied: "+name );
            return m;
        }
    }
    
    public static void main( String[] args ) throws IOException {
        Gson gson= new GsonBuilder().setPrettyPrinting().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();
        //URL url= HapiJsonSorter.class.getResource("info.json");
        
        String n;
        if ( args.length<1 ) {
            System.err.println("java -cp HapiJsonSorter.jar <file.json>");
            System.err.println("   where file.json is info, catalog, info schema, catalog schema, combined schemas, etc.");
            System.exit(-1);
        }
        n= args[0];
        String out="-";
        if ( args.length==2 ) {
            out= args[1];
        }
        
        URL url= new File(n).toURI().toURL();
        Map m= gson.fromJson( new InputStreamReader(url.openStream()), LinkedHashMap.class );
        if ( m.containsKey("type") || m.containsKey("definitions") ) {
            m= sortMap( FileType.HAPI_SCHEMA, "", m, 0 );
        } else if ( m.containsKey("HAPIDateTime") ) {
            m= sortMap( FileType.HAPI_COMBINED_SCHEMA, "", m, 0 );
        } else {
            m= sortMap( FileType.HAPI, "", m, 0 );
        }
        
        String s= gson.toJson(m, LinkedHashMap.class);
        
        if ( out.equals("-") ) {
            System.out.println(s);
            System.out.println("----");
            System.out.println(n);
        } else {
            new File(out).getParentFile().mkdirs();
            new FileWriter(out).write( s );
            System.out.println("----");
            System.out.println(n);
        }
    }
}
