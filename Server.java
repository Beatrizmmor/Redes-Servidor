// import java.io.*;
// import java.net.*;
// import java.nio.*;
// import java.nio.channels.*;
// import java.nio.charset.*;
// import java.util.*;

// public class Server
// {
//   // A pre-allocated buffer for the received data
//   static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

//   // Decoder for incoming text -- assume UTF-8
//   static private final Charset charset = Charset.forName("UTF8");
//   static private final CharsetDecoder decoder = charset.newDecoder();


//   static public void main( String args[] ) throws Exception {
//     // Parse port from command line
//     int port = Integer.parseInt( args[0] );
    
//     try {
//       // Instead of creating a ServerSocket, create a ServerSocketChannel
//       ServerSocketChannel ssc = ServerSocketChannel.open();

//       // Set it to non-blocking, so we can use select
//       ssc.configureBlocking( false );

//       // Get the Socket connected to this channel, and bind it to the
//       // listening port
//       ServerSocket ss = ssc.socket();
//       InetSocketAddress isa = new InetSocketAddress( port );
//       ss.bind( isa );

//       // Create a new Selector for selecting
//       Selector selector = Selector.open();

//       // Register the ServerSocketChannel, so we can listen for incoming
//       // connections
//       ssc.register( selector, SelectionKey.OP_ACCEPT );
//       System.out.println( "Listening on port "+port );

//       while (true) {
//         // See if we've had any activity -- either an incoming connection,
//         // or incoming data on an existing connection
//         int num = selector.select();

//         // If we don't have any activity, loop around and wait again
//         if (num == 0) {
//           continue;
//         }

//         // Get the keys corresponding to the activity that has been
//         // detected, and process them one by one
//         Set<SelectionKey> keys = selector.selectedKeys();
//         Iterator<SelectionKey> it = keys.iterator();
//         while (it.hasNext()) {
//           // Get a key representing one of bits of I/O activity
//           SelectionKey key = it.next();

//           // What kind of activity is it?
//           if (key.isAcceptable()) {

//             // It's an incoming connection.  Register this socket with
//             // the Selector so we can listen for input on it
//             Socket s = ss.accept();
//             System.out.println( "Got connection from "+s );

//             // Make sure to make it non-blocking, so we can use a selector
//             // on it.
//             SocketChannel sc = s.getChannel();
//             sc.configureBlocking( false );

//             // Register it with the selector, for reading
//             sc.register( selector, SelectionKey.OP_READ );

//           } else if (key.isReadable()) {

//             SocketChannel sc = null;

//             try {

//               // It's incoming data on a connection -- process it
//               sc = (SocketChannel)key.channel();
//               boolean ok = processInput( key, selector );

//               // If the connection is dead, remove it from the selector
//               // and close it
//               if (!ok) {
//                 key.cancel();

//                 Socket s = null;
//                 try {
//                   s = sc.socket();
//                   System.out.println( "Closing connection to "+s );
//                   s.close();
//                 } catch( IOException ie ) {
//                   System.err.println( "Error closing socket "+s+": "+ie );
//                 }
//               }

//             } catch( IOException ie ) {

//               // On exception, remove this channel from the selector
//               key.cancel();

//               try {
//                 sc.close();
//               } catch( IOException ie2 ) { System.out.println( ie2 ); }

//               System.out.println( "Closed "+sc );
//             }
//           }
//         }

//         // We remove the selected keys, because we've dealt with them.
//         keys.clear();
//       }
//     } catch( IOException ie ) {
//       System.err.println( ie );
//     }
//   }


//   // Just read the message from the socket and send it to stdout
//   static private boolean processInput( SelectionKey key, Selector s ) throws IOException {
//     SocketChannel sc = (SocketChannel) key.channel();
//     // Read the message to the buffer
//     buffer.clear();
//     sc.read( buffer );
//     buffer.flip();

//     // If no data, close the connection
//     if (buffer.limit()==0) {
//       return false;
//     }

//     String text = decoder.decode( buffer ).toString().trim();
//     if(text.isEmpty()) return true;

//     if(key.attachment() == null) {
//       key.attach(text);
//       return true;
//     }

//     String nick = (String) key.attachment();
//     ByteBuffer out = charset.encode(nick + ": " + text + "\r\n");

//     // difundir para todos os clientes registados no seletor
//     for (SelectionKey k : s.keys()) {
//       if (k.isValid() && k.channel() instanceof SocketChannel) {
//         SocketChannel dest = (SocketChannel) k.channel();

//         out.rewind();
//         try{
//           while (out.hasRemaining()) {
//             dest.write( out );
//           }
//         } catch( IOException ie) {
//           k.cancel();
//           try{dest.close();} catch( IOException ie2) {}
//           System.out.println("Closed" +dest);
//         }
//       }
//     }

//     // Decode and print the message to stdout
//     // String message = decoder.decode(buffer).toString();
//     // System.out.print( message );
//     return true;
//   }
// }


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
