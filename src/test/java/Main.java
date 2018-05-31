import com.codingame.game.ThibaudPlayer;
import com.codingame.gameengine.runner.GameRunner;

import java.util.Properties;

public class Main {
    public static void main(String[] args) {

        /*Properties props = new Properties();
        props.load(this.getClass().getResourceAsStream("project.properties"));
        String basedir = props.get("project.basedir").toString();
        System.out.println(basedir);*/
        System.out.println("Working Directory = " +
                System.getProperty("user.dir"));

        GameRunner gameRunner = new GameRunner();

        // Adds as many player as you need to test your game
        gameRunner.addAgent(Level1Player.class);
        gameRunner.addAgent(ThibaudPlayer.class);

        // gameRunner.addCommandLinePlayer("python3 /home/user/player.py");

        gameRunner.start();
    }
}
