import org.apache.commons.codec.digest.Crypt
fun main(args: Array<String>) {
    if(args.size!=2){
        System.err.println("usage: java -cp owntracks.jar Cryptor username password")
    }
    println(Crypt.crypt(args[1], args[0]))

}