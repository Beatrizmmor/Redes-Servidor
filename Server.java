import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class Server
{
  // Buffer pré-alocado para os dados recebidos
  static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

  // Descodificador / codificador de texto -- UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();


  static public void main( String args[] ) throws Exception {
    int port = Integer.parseInt( args[0] );

    try {
      ServerSocketChannel ssc = ServerSocketChannel.open();
      ssc.configureBlocking( false );

      ServerSocket ss = ssc.socket();
      InetSocketAddress isa = new InetSocketAddress( port );
      ss.bind( isa );

      Selector selector = Selector.open();
      ssc.register( selector, SelectionKey.OP_ACCEPT );
      System.out.println( "Listening on port "+port );

      while (true) {
        int num = selector.select();
        if (num == 0) {
          continue;
        }

        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) {
          SelectionKey key = it.next();

          if (key.isAcceptable()) {

            Socket s = ss.accept();
            System.out.println( "Got connection from "+s );

            SocketChannel sc = s.getChannel();
            sc.configureBlocking( false );

            // Sem anexo (null) = cliente ainda não indicou o pseudónimo
            sc.register( selector, SelectionKey.OP_READ );

          } else if (key.isReadable()) {

            SocketChannel sc = null;

            try {
              sc = (SocketChannel)key.channel();
              boolean ok = processInput( key, selector );

              if (!ok) {
                key.cancel();

                Socket s = null;
                try {
                  s = sc.socket();
                  System.out.println( "Closing connection to "+s );
                  s.close();
                } catch( IOException ie ) {
                  System.err.println( "Error closing socket "+s+": "+ie );
                }
              }

            } catch( IOException ie ) {
              key.cancel();

              try {
                sc.close();
              } catch( IOException ie2 ) { System.out.println( ie2 ); }

              System.out.println( "Closed "+sc );
            }
          }
        }

        keys.clear();
      }
    } catch( IOException ie ) {
      System.err.println( ie );
    }
  }


  // Lê a mensagem da socket. A 1.ª é o pseudónimo; as seguintes são difundidas.
  static private boolean processInput( SelectionKey key, Selector selector ) throws IOException {
    SocketChannel sc = (SocketChannel)key.channel();

    buffer.clear();
    sc.read( buffer );
    buffer.flip();

    // Sem dados: fechar a conexão
    if (buffer.limit()==0) {
      return false;
    }

    String message = decoder.decode(buffer).toString();

    String nick = (String)key.attachment();
    if (nick == null) {
      // Primeira linha = pseudónimo; guardá-lo na key
      key.attach( message.trim() );
    } else {
      broadcast( selector, nick+": "+message );
    }

    return true;
  }


  // Envia o texto a todos os clientes que já têm pseudónimo
  static private void broadcast( Selector selector, String text ) {
    for (SelectionKey k : selector.keys()) {
      if (k.isValid() && k.channel() instanceof SocketChannel && k.attachment() != null) {
        try {
          ((SocketChannel)k.channel()).write( charset.encode( text ) );
        } catch( IOException ie ) {
          System.err.println( "Erro a enviar para "+k.channel()+": "+ie );
        }
      }
    }
  }
}
