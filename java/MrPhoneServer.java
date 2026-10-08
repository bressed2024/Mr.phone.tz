import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
class MrPhoneServer {
private static final int PORT=Integer.parseInt(System.getenv().getOrDefault("PORT","8080"));
private static final String PASSWORD=System.getenv("MR_PHONE_ADMIN_PASSWORD");
private static final String ORIGIN=System.getenv().getOrDefault("MR_PHONE_ALLOWED_ORIGIN","http://localhost:5500");
private static final Path DATA=Paths.get(System.getenv().getOrDefault("MR_PHONE_DATA_DIR","data"));
private static final Map<String,Long> SESSIONS=new HashMap<>();
private static final Pattern IMAGE=Pattern.compile("^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$");
public static void main(String[] args)throws Exception{
if(PASSWORD==null||PASSWORD.length()<12)throw new IllegalStateException("Set MR_PHONE_ADMIN_PASSWORD to a password of at least 12 characters");
Files.createDirectories(DATA);Files.createDirectories(DATA.resolve("uploads"));
HttpServer s=HttpServer.create(new InetSocketAddress(PORT),0);
s.createContext("/api/login",MrPhoneServer::login);s.createContext("/api/logout",MrPhoneServer::logout);s.createContext("/api/products",MrPhoneServer::products);s.createContext("/api/upload",MrPhoneServer::upload);s.createContext("/api/feedback",MrPhoneServer::feedback);
s.setExecutor(Executors.newFixedThreadPool(8));s.start();System.out.println("MR. PHONE TZ API listening on "+PORT);
}
private static void login(HttpExchange x)throws IOException{if(options(x))return;if(!method(x,"POST"))return;String p=field(read(x),"password");if(p==null||!MessageDigest.isEqual(p.getBytes(StandardCharsets.UTF_8),PASSWORD.getBytes(StandardCharsets.UTF_8))){send(x,401,"{\"error\":\"Invalid credentials\"}");return;}String t=UUID.randomUUID().toString().replace("-","");synchronized(SESSIONS){SESSIONS.put(t,System.currentTimeMillis()+86400000L);}send(x,200,"{\"token\":\"" + t + "\"}");}
private static void logout(HttpExchange x)throws IOException{if(options(x))return;String t=x.getRequestHeaders().getFirst("Authorization");if(t!=null)synchronized(SESSIONS){SESSIONS.remove(t.replace("Bearer ",""));}send(x,204,"");}
private static void products(HttpExchange x)throws IOException{if(options(x))return;if("GET".equals(x.getRequestMethod())){send(x,200,readFile("products.json","[]"));return;}if(!authorized(x))return;if("POST".equals(x.getRequestMethod())){append("products.json",read(x));send(x,201,"{\"ok\":true}");return;}send(x,405,"{\"error\":\"Method not allowed\"}");}
private static void feedback(HttpExchange x)throws IOException{if(options(x))return;if("GET".equals(x.getRequestMethod())){send(x,200,readFile("feedback.json","[]"));return;}if(!authorized(x))return;if("POST".equals(x.getRequestMethod())){append("feedback.json",read(x));send(x,201,"{\"ok\":true}");return;}send(x,405,"{\"error\":\"Method not allowed\"}");}
private static void upload(HttpExchange x)throws IOException{if(options(x))return;if(!authorized(x))return;String b=read(x),im=field(b,"image"),n=field(b,"name");if(im==null||n==null||n.length()>80||!IMAGE.matcher(im).matches()||im.length()>8000000){send(x,400,"{\"error\":\"Invalid image or filename\"}");return;}byte[] data=Base64.getDecoder().decode(im.substring(im.indexOf(',')+1));String safe=n.replaceAll("[^a-zA-Z0-9._-]","_");Files.write(DATA.resolve("uploads").resolve(safe),data,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);send(x,201,"{\"file\":\"uploads/"+safe+"\"}");}
private static boolean authorized(HttpExchange x)throws IOException{String a=x.getRequestHeaders().getFirst("Authorization");if(a==null||!a.startsWith("Bearer ")){send(x,401,"{\"error\":\"Login required\"}");return false;}synchronized(SESSIONS){Long e=SESSIONS.get(a.substring(7));if(e!=null&&e>System.currentTimeMillis())return true;}send(x,401,"{\"error\":\"Session expired\"}");return false;}
private static boolean method(HttpExchange x,String e)throws IOException{if(e.equals(x.getRequestMethod()))return true;send(x,405,"{\"error\":\"Method not allowed\"}");return false;}
private static boolean options(HttpExchange x)throws IOException{if(!"OPTIONS".equals(x.getRequestMethod()))return false;send(x,204,"");return true;}
private static String field(String j,String k){String q="\"" + k + "\"";int p=j.indexOf(q);if(p<0)return null;p=j.indexOf(':',p)+1;while(p<j.length()&&Character.isWhitespace(j.charAt(p)))p++;if(p>=j.length()||j.charAt(p)!='\"')return null;int e=j.indexOf('\"',p+1);return e<0?null:j.substring(p+1,e);}
private static String read(HttpExchange x)throws IOException{return new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);}
private static String readFile(String n,String f)throws IOException{Path p=DATA.resolve(n);return Files.exists(p)?Files.readString(p):f;}
private static void append(String n,String item)throws IOException{String o=readFile(n,"[]").trim();if(!o.startsWith("[")||!o.endsWith("]"))o="[]";String v=o.length()<=2?"["+item+"]":o.substring(0,o.length()-1)+","+item+"]";Files.writeString(DATA.resolve(n),v,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);}
private static void send(HttpExchange x,int st,String b)throws IOException{x.getResponseHeaders().set("Access-Control-Allow-Origin",ORIGIN);x.getResponseHeaders().set("Access-Control-Allow-Headers","Content-Type, Authorization");x.getResponseHeaders().set("Access-Control-Allow-Methods","GET, POST, DELETE, OPTIONS");x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");byte[] z=b.getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(st,b.isEmpty()?-1:z.length);if(!b.isEmpty())try(OutputStream o=x.getResponseBody()){o.write(z);}}
                                                           }
