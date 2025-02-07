import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionFormCreator;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class PhotoInjectorLauncher extends ExtensionFormCreator {

    @Override
    public ExtensionForm createForm(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("PhotoInjector.fxml"));
        Parent root = loader.load();
        primaryStage.setTitle("PhotoInjector");
        primaryStage.setScene(new Scene(root));
        primaryStage.setResizable(false);
        return loader.getController();
    }

    public static void main(String[] args) {
        runExtensionForm(args, PhotoInjectorLauncher.class);
    }
}