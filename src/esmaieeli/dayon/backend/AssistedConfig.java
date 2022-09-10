/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package esmaieeli.dayon.backend;

/**
 *
 * @author user
 */
public class AssistedConfig {
    public String ip;
    public String port;
    public int reconnectDelaySeconds;
    public AssistedConfig(String passedIp, String passedPort, int passedReconnectDelaySeconds){
        ip=passedIp;
        port=passedPort;
        reconnectDelaySeconds=passedReconnectDelaySeconds;
    }
    
    public AssistedConfig(){
        
    }
}
