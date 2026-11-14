package com.tecnopc.ui;

import com.fasterxml.jackson.databind.*;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Consumer;

/** Shared desktop design system and native-client connection. */
public final class DesktopShell extends BorderPane {
    private static void trace(String event) {
        try {
            var folder=java.nio.file.Path.of(System.getProperty("user.home"),".tecnopc");
            java.nio.file.Files.createDirectories(folder);
            java.nio.file.Files.writeString(folder.resolve("desktop-session.log"),java.time.Instant.now()+" "+event+System.lineSeparator(),java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
        }catch(Exception ignored){}
    }
    public final ObjectMapper json = new ObjectMapper();
    public final TextField server = new TextField(System.getProperty("tecnopc.api.url", System.getenv().getOrDefault("TECNOPC_API_URL", "http://localhost:8081")));
    public final TextField user = new TextField();
    public final PasswordField password = new PasswordField();
    private final VBox navigation = new VBox(6);
    private final StackPane body = new StackPane();
    private final Label title = text("Inicio", "page-title");
    private final Label subtitle = text("Tu espacio de trabajo", "muted");
    private final Label connection = text("Sin conectar", "connection-state");
    private final Label status = text("Conecta tu cuenta para consultar los datos del negocio.", "status-text");
    private final Label account = text("Sesión sin iniciar", "account-pill");
    private final TitledPane connectionPane;
    private final ProgressIndicator progress = new ProgressIndicator();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private int requests;
    private long identityVersion;
    private boolean authenticated;
    private final String loginEndpoint;
    private VBox workspace;
    private Node sidebar;
    private HBox header;
    private Button logoutButton;
    private final List<Button> navButtons = new ArrayList<>();

    public DesktopShell(String role, String roleName) {
        trace("OPEN session-diagnostic-v1 role="+role+" library="+DesktopShell.class.getProtectionDomain().getCodeSource().getLocation());
        loginEndpoint=switch(role){case "admin"->"/api/admin/resumen";case "vendedor"->"/api/vendedor/productos";default->"/api/tecnico/reparaciones";};
        getStyleClass().addAll("desktop-shell", role);
        var brandIcon=new javafx.scene.image.ImageView(new javafx.scene.image.Image(Objects.requireNonNull(DesktopShell.class.getResourceAsStream("tecnopc-icon.png"))));brandIcon.setFitWidth(40);brandIcon.setFitHeight(40);brandIcon.setPreserveRatio(true);
        var wordmark=new HBox(0,text("Tecno","brand"),text("PC","brand-accent"));var logo = new HBox(9,brandIcon,wordmark);logo.setAlignment(Pos.CENTER_LEFT);
        var description = text(roleName.toUpperCase(Locale.ROOT), "sidebar-caption");
        VBox side = new VBox(24, new VBox(4, logo, description), navigation);
        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        side.getChildren().addAll(spacer, text("Un negocio. Todo conectado.", "sidebar-footer"), connection);
        side.getStyleClass().add("sidebar"); side.setPrefWidth(220); setLeft(side);

        user.setPromptText("Usuario"); password.setPromptText("Contraseña"); server.setPromptText("https://tu-servidor");
        user.setPrefWidth(155); password.setPrefWidth(155); server.setPrefWidth(320);
        var connect = primary("Conectar", () -> request("GET",loginEndpoint,null,data->{authenticated=true;setLocked(false);if(onConnect!=null)onConnect.run();}));
        user.textProperty().addListener((o,a,b)->identityVersion++);password.textProperty().addListener((o,a,b)->identityVersion++);server.textProperty().addListener((o,a,b)->identityVersion++);
        var disconnect = secondary("Salir", () -> { identityVersion++;password.clear();if(onDisconnect!=null)onDisconnect.run();account.setText("Sesión sin iniciar"); connection.setText("Sin conectar"); connectionPaneExpanded(); body.getChildren().setAll(empty("Sesión cerrada", "Conecta una cuenta para continuar.")); status("Sesión cerrada en este equipo."); });
        logoutButton=disconnect;
        connect.setMaxWidth(Double.MAX_VALUE);connect.setMinWidth(160);disconnect.setMinWidth(100);
        var actions=new HBox(12,connect);HBox.setHgrow(connect,Priority.ALWAYS);
        var loginMessage=text("","status-text");loginMessage.textProperty().bind(status.textProperty());loginMessage.setWrapText(true);
        var loginIcon=new javafx.scene.image.ImageView(brandIcon.getImage());loginIcon.setFitWidth(40);loginIcon.setFitHeight(40);loginIcon.setPreserveRatio(true);
        var loginBrand=new HBox(12,loginIcon,text("TecnoPC · Iniciar sesión","login-heading"));loginBrand.setAlignment(Pos.CENTER_LEFT);
        var inputs = new VBox(18,loginBrand,field("Servidor",server),field("Usuario",user),field("Contraseña",password),actions,loginMessage);
        inputs.setPadding(new Insets(12));
        server.setMaxWidth(Double.MAX_VALUE);user.setMaxWidth(Double.MAX_VALUE);password.setMaxWidth(Double.MAX_VALUE);
        connectionPane = new TitledPane("Conexión y acceso",inputs);connectionPane.setAnimated(false); connectionPane.setExpanded(true); connectionPane.getStyleClass().add("connection-pane");
        VBox titles = new VBox(5,title,subtitle); Region headerSpace = new Region(); HBox.setHgrow(headerSpace,Priority.ALWAYS);
        header = new HBox(16,titles,headerSpace,account,disconnect); header.setAlignment(Pos.CENTER_LEFT);
        sidebar=side;
        workspace = new VBox(20,header,connectionPane,body); workspace.getStyleClass().add("workspace"); VBox.setVgrow(body,Priority.ALWAYS); setCenter(workspace);
        setLocked(true);
        user.textProperty().addListener((o,a,b)->{if(authenticated)trace("LOCK username-field-changed");invalidateSession();});
        password.textProperty().addListener((o,a,b)->{if(authenticated)trace("LOCK password-field-changed-or-logout");invalidateSession();});
        server.textProperty().addListener((o,a,b)->{if(authenticated)trace("LOCK server-field-changed");invalidateSession();});
        password.setOnAction(e->connect.fire());
        progress.setMaxSize(16,16); progress.setVisible(false); progress.setManaged(false);
        var footer = new HBox(10,progress,status); footer.getStyleClass().add("status-bar");setBottom(footer);
        setMinSize(900,620);
    }
    private Runnable onConnect;
    private void setLocked(boolean locked) {
        workspace.setAlignment(locked?Pos.CENTER:Pos.TOP_LEFT);
        connectionPane.setMaxWidth(locked?480:Double.MAX_VALUE);
        connectionPane.setVisible(locked);connectionPane.setManaged(locked);
        logoutButton.setVisible(!locked);logoutButton.setManaged(!locked);
        for(Node node:List.of(sidebar,header,body)){node.setVisible(!locked);node.setManaged(!locked);}
        connectionPane.setExpanded(true);
        connectionPane.setCollapsible(false);
    }
    private void invalidateSession() {
        identityVersion++;
        authenticated=false;setLocked(true);body.getChildren().clear();
        if(onDisconnect!=null)onDisconnect.run();
        account.setText("Sesión sin iniciar");connection.setText("Sin conectar");
    }
    private Runnable onDisconnect;
    public void onConnect(Runnable action) { onConnect=action; }
    public void onDisconnect(Runnable action) { onDisconnect=action; }
    private void connectionPaneExpanded() { connectionPane.setExpanded(true); }
    public void nav(String label,Runnable action) {
        String name=label.replaceFirst("^\\S+\\s+","");Button button = new Button(name);button.setGraphic(navIcon(name));button.setGraphicTextGap(12); button.setMaxWidth(Double.MAX_VALUE);button.getStyleClass().add("nav-button");
        button.setOnAction(e->{navButtons.forEach(b->b.getStyleClass().remove("active"));button.getStyleClass().add("active");action.run();});
        if(navButtons.isEmpty()) button.getStyleClass().add("active");
        navButtons.add(button); navigation.getChildren().add(button);
    }
    private static SVGPath navIcon(String label) {
        String name=label.toLowerCase(Locale.ROOT),path;
        if(name.contains("inicio"))path="M2 10L10 3L18 10M5 9V18H15V9M9 18V12H12V18";
        else if(name.contains("venta"))path="M2 3H5L8 14H17L19 6H6M9 18H9.1M16 18H16.1";
        else if(name.contains("usuario")||name.contains("equipo")&&!name.contains("estado"))path="M13 6A3 3 0 1 1 7 6A3 3 0 1 1 13 6M4 18V16C4 11 16 11 16 16V18M16 6A2 2 0 0 1 17 10";
        else if(name.contains("aviso"))path="M2 5H18V17H2ZM2 6L10 12L18 6";
        else if(name.contains("alerta"))path="M10 2L19 18H1ZM10 8V12M10 15V15.1";
        else if(name.contains("historial"))path="M18 10A8 8 0 1 1 2 10A8 8 0 1 1 18 10M10 5V10L14 12";
        else if(name.contains("pedido"))path="M3 3H17V18H3ZM6 10L9 13L14 7";
        else if(name.contains("diagn")||name.contains("servicio"))path="M13 3L10 6L14 10L17 7C19 13 14 15 11 13L5 19L1 15L7 9C5 5 8 1 13 3Z";
        else if(name.contains("recep")||name.contains("repar"))path="M5 4H16V19H3V4ZM7 2H13V6H7ZM6 10H13M6 14H13";
        else path="M2 3H8V9H2ZM12 3H18V9H12ZM2 13H8V19H2ZM12 13H18V19H12Z";
        SVGPath icon=new SVGPath();icon.setContent(path);icon.getStyleClass().add("nav-icon");return icon;
    }
    public void page(String name,String detail,Node content) { if(!authenticated)return;title.setText(name);subtitle.setText(detail);body.getChildren().setAll(content); }
    public void prepend(Node controls) {Node old=body.getChildren().isEmpty()?new Pane():body.getChildren().getFirst();body.getChildren().clear();VBox layout=new VBox(16,controls,old);VBox.setVgrow(old,Priority.ALWAYS);body.getChildren().add(layout);}
    public void status(String message) { status.setText(message);status.getStyleClass().remove("error"); }
    public void error(String message) { status.setText(message);if(!status.getStyleClass().contains("error"))status.getStyleClass().add("error"); }
    public boolean credentialsPresent() { return !user.getText().isBlank() && !password.getText().isBlank(); }
    public void request(String method,String path,JsonNode payload,Consumer<JsonNode> success) { request(method,path,payload,success,()->{}); }
    public void request(String method,String path,JsonNode payload,Consumer<JsonNode> success,Runnable finished) {
        request(method,path,payload,success,finished,null);
    }
    public void request(String method,String path,JsonNode payload,Consumer<JsonNode> success,Runnable finished,String idempotencyKey) {
        if(!authenticated && !(method.equals("GET")&&path.equals(loginEndpoint))){finished.run();return;}
        if(!credentialsPresent()){error("Ingresa tu usuario y contraseña en Conexión y acceso.");connectionPane.setExpanded(true);finished.run();return;}
        try {
            URI uri=URI.create(server.getText().trim().replaceAll("/+$","")+path);
            boolean local=Set.of("localhost","127.0.0.1","[::1]","::1").contains(Objects.toString(uri.getHost(),"").toLowerCase(Locale.ROOT));
            if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null || uri.getRawQuery()!=null ||
                !("https".equalsIgnoreCase(uri.getScheme()) || (local&&"http".equalsIgnoreCase(uri.getScheme()))))
                throw new IllegalArgumentException("Usa HTTPS para servidores remotos.");
            String authorization=Base64.getEncoder().encodeToString((user.getText()+":"+password.getText()).getBytes(StandardCharsets.UTF_8));
            var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25)).header("Authorization","Basic "+authorization);
            if(idempotencyKey!=null)builder.header("Idempotency-Key",idempotencyKey);
            if(method.equals("GET"))builder.GET();else builder.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(payload==null?"{}":payload.toString()));
            requests++;progress.setVisible(true);progress.setManaged(true);status("Consultando el servidor…");
            long generation=identityVersion;
            http.sendAsync(builder.build(),HttpResponse.BodyHandlers.ofString()).whenComplete((response,failure)->Platform.runLater(()->{
                try {
                    if(generation!=identityVersion)return;
                    if(failure!=null){trace("NETWORK_FAILURE "+method+" "+path);error("No se pudo conectar. Comprueba el servidor y tu conexión.");return;}
                    trace("HTTP "+response.statusCode()+" "+method+" "+path+" authenticated="+authenticated);
                    if(response.statusCode()==401){invalidateSession();error("Usuario o contraseña incorrectos. Vuelve a iniciar sesión.");return;}
                    if(response.statusCode()==429){
                        String wait=response.headers().firstValue("Retry-After").filter(v->v.matches("[0-9]{1,4}")).orElse("60");
                        error("Demasiados intentos. Espera "+wait+" segundos antes de volver a intentar.");return;
                    }
                    if(response.statusCode()==403){if(!authenticated)invalidateSession();error("El servidor no permite esta operación. Comprueba los permisos y que el backend esté actualizado.");return;}
                    JsonNode data=json.readTree(response.body());
                    if(response.statusCode()<200 || response.statusCode()>=300){error(switch(response.statusCode()){case 401->"Usuario o contraseña incorrectos.";case 403->"Tu cuenta no tiene permiso para esta operación.";default->data==null?"Error HTTP "+response.statusCode():data.path("mensaje").asText("Error HTTP "+response.statusCode());});return;}
                    connection.setText("●  API conectada");account.setText("●  "+user.getText());connectionPane.setExpanded(false);status("Datos actualizados desde el servidor.");success.accept(data);
                } catch(Exception ex){trace("CALLBACK_ERROR "+path+" "+ex.getClass().getSimpleName());error("No se pudo interpretar la respuesta del servidor.");}
                finally {requests=Math.max(0,requests-1);progress.setVisible(requests>0);progress.setManaged(requests>0);finished.run();}
            }));
        } catch(Exception ex){error("Revisa la URL del servidor; para acceso remoto usa HTTPS.");finished.run();}
    }
    public void show(Stage stage,String windowTitle,List<String> args) {
        Scene scene=new Scene(this,1240,820);scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("desktop.css")).toExternalForm());
        stage.getIcons().setAll(new javafx.scene.image.Image(Objects.requireNonNull(DesktopShell.class.getResourceAsStream("tecnopc-icon.png"))));
        stage.setMinWidth(960);stage.setMinHeight(680);stage.setTitle(windowTitle);stage.setScene(scene);stage.show();
        args.stream().filter(a->a.startsWith("--preview=")).findFirst().ifPresent(a->Platform.runLater(()->{
            try {applyCss();layout();WritableImage image=scene.snapshot(null);int w=(int)image.getWidth(),h=(int)image.getHeight();int[] pixels=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);var png=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);png.setRGB(0,0,w,h,pixels,0,w);javax.imageio.ImageIO.write(png,"png",new java.io.File(a.substring(10)));}
            catch(Exception ex){System.err.println("No se pudo generar la vista previa: "+ex.getClass().getSimpleName());}
            finally{Platform.exit();}
        }));
    }
    public record Column(String title,String key,boolean money) { public Column(String title,String key){this(title,key,false);} }
    public static TableView<JsonNode> table(JsonNode data,Column... columns) {
        TableView<JsonNode> table=new TableView<>();table.getStyleClass().add("data-table");table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        for(Column column:columns){TableColumn<JsonNode,String> cell=new TableColumn<>(column.title());cell.setCellValueFactory(row->{var value=row.getValue().path(column.key());return new ReadOnlyStringWrapper(value.isNull()||value.isMissingNode()?"—":column.money()?money(value.decimalValue()):value.isBoolean()?(value.asBoolean()?"Sí":"No"):value.asText());});table.getColumns().add(cell);}
        if(data!=null&&data.isArray())data.forEach(table.getItems()::add);
        table.setPlaceholder(empty("Sin registros", "Los datos aparecerán aquí cuando estén disponibles."));table.setPrefHeight(410);return table;
    }
    public static Node searchable(TableView<JsonNode> table,String placeholder) {
        var filter=new TextField();filter.setPromptText(placeholder);filter.getStyleClass().add("search-field");var items=new FilteredList<>(FXCollections.observableArrayList(table.getItems()),x->true);table.setItems(items);
        filter.textProperty().addListener((o,old,value)->items.setPredicate(row->row.toString().toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT))));VBox box=new VBox(14,filter,table);VBox.setVgrow(table,Priority.ALWAYS);return box;
    }
    public static Label text(String value,String style) {Label label=new Label(value);label.getStyleClass().add(style);label.setWrapText(true);return label;}
    public static VBox field(String name,Node input) {if(input instanceof Region region)region.setMaxWidth(Double.MAX_VALUE);VBox box=new VBox(6,text(name,"field-label"),input);box.setMaxWidth(Double.MAX_VALUE);return box;}
    public static VBox card(String title,Node... children) {VBox box=new VBox(16);box.getStyleClass().add("card");if(title!=null&&!title.isBlank())box.getChildren().add(text(title,"card-title"));box.getChildren().addAll(children);return box;}
    public static VBox metric(String title,String value,String note) {VBox box=new VBox(8,text(title,"metric-label"),text(value,"metric-value"),text(note,"muted"));box.getStyleClass().add("metric-card");box.setMaxWidth(Double.MAX_VALUE);return box;}
    public static HBox row(Node... children){HBox box=new HBox(16,children);for(Node child:children)if(child instanceof Region)HBox.setHgrow(child,Priority.ALWAYS);return box;}
    public static Node empty(String title,String note){VBox box=new VBox(9,text("◈","empty-symbol"),text(title,"card-title"),text(note,"muted"));box.setAlignment(Pos.CENTER);box.setPadding(new Insets(30));return box;}
    public static Button primary(String label,Runnable action){Button b=new Button(label);b.getStyleClass().add("primary-button");b.setOnAction(e->action.run());return b;}
    public static Button secondary(String label,Runnable action){Button b=new Button(label);b.getStyleClass().add("secondary-button");b.setOnAction(e->action.run());return b;}
    public static ScrollPane scroll(Node content){ScrollPane pane=new ScrollPane(content);pane.setFitToWidth(true);pane.getStyleClass().add("content-scroll");return pane;}
    public static String money(BigDecimal amount){return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO")).format(amount);}
    public static boolean confirm(String title,String message){Alert alert=new Alert(Alert.AlertType.CONFIRMATION,message,ButtonType.YES,ButtonType.NO);alert.setTitle(title);alert.setHeaderText(title);return alert.showAndWait().orElse(ButtonType.NO)==ButtonType.YES;}
}
