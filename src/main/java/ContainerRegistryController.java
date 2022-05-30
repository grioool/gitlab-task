import java.io.IOException;

public class ContainerRegistryController {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Some arguments are missing, please enter correct command: cmd <base_url> <token>");
            return;
        }
        GitLabClient client = new GitLabClient(
                args[0],
                args[1]
        );
        try {
            client.getPaths(617).forEach(System.out::println);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}