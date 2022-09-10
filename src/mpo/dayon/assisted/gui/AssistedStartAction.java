package mpo.dayon.assisted.gui;

import mpo.dayon.common.gui.common.ImageNames;
import mpo.dayon.common.gui.common.ImageUtilities;
import mpo.dayon.common.log.Log;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.ExecutionException;

import static java.lang.String.format;
import static mpo.dayon.common.babylon.Babylon.translate;

public class AssistedStartAction extends AbstractAction {
    public final transient Assisted assisted;//EDIT BY AMIROOO

    public AssistedStartAction(Assisted assisted) {
        this.assisted = assisted;

        putValue(Action.NAME, "start");
        putValue(Action.SHORT_DESCRIPTION, translate("connect.assistant"));
        putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.START));
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        assisted.onReady();
        new NetWorker().execute();
    }
    
    //EDIT BY AMIROOO
    public void myActionPerformed(String ip, String port) {
        assisted.onReady();
        NetWorker netWorker = new NetWorker();
        netWorker.isStartedFromOutsideTimer = true;
        netWorker.passedIp = ip;
        netWorker.passedPort = port;
        netWorker.execute();
    }

    class NetWorker extends SwingWorker<String, String> {
        public boolean isStartedFromOutsideTimer = false;//EDIT BY AMIROOO
        public String passedIp;//EDIT BY AMIROOO
        public String passedPort;//EDIT BY AMIROOO
        
        @Override
        protected String doInBackground() {//EDIT BY AMIROOO
            if (isStartedFromOutsideTimer == false) {//EDIT BY AMIROOO
                if (assisted.start() && !isCancelled()) {//EDIT BY AMIROOO
                    assisted.connect();//EDIT BY AMIROOO
                }//EDIT BY AMIROOO
            } else {//EDIT BY AMIROOO
                assisted.start(passedIp,passedPort);//EDIT BY AMIROOO
            }//EDIT BY AMIROOO
            return null;//EDIT BY AMIROOO
            //if (assisted.start() && !isCancelled()) {
            //    assisted.connect();
            //}
            //return null;
        }

        @Override
        protected void done() {
            try {
                if (!isCancelled()) {
                    super.get();
                    Log.debug(format("NetWorker is done [%s]", assisted.getConfiguration().getServerName()));
                }
            } catch (InterruptedException | ExecutionException ie) {
                Log.info("NetWorker was cancelled");
                Thread.currentThread().interrupt();
            }
        }
    }
}
