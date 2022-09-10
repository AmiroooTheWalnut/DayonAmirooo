package mpo.dayon.common.error;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import mpo.dayon.common.log.Log;

import static java.lang.String.format;
import static mpo.dayon.common.babylon.Babylon.translate;

public abstract class FatalErrorHandler {
    private static JFrame frame;

    private FatalErrorHandler() {
    }

    /**
     * Displays a translated error message and terminates
     */
    public static void bye(String message, Throwable error) {
        Log.fatal(message, error);
        Log.fatal("Bye!");

        if (frame != null) {
            String info = error.getMessage();

            if (info == null) {
                info = translate("fatal.error.msg3");
            } else {
                info = translate(info);
            }

            JOptionPane.showMessageDialog(frame, format("%s\n%s", translate("fatal.error.msg1"), translate("fatal.error.msg2", info)),
                    translate("fatal.error"), JOptionPane.ERROR_MESSAGE);
        }

        //System.exit(-1);//REMOVED BY AMIROOO
    }

    public static void attachFrame(JFrame frame) {
        FatalErrorHandler.frame = frame;
    }
}
