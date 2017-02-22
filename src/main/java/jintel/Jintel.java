package jintel;

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.SwingUtilities;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.JFXPanel;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.tbee.javafx.scene.layout.MigPane;

/**
 * Created by smithj on 2/15/17.
 */
public class Jintel
{
    private final Pattern mMessageTimePattern = Pattern.compile("\\[ \\d\\d\\d\\d\\.\\d\\d\\.\\d\\d \\d\\d:\\d\\d:\\d\\d \\].*");

    private EnumMap<Settings, ListView<String>> mSettingsListViews = new EnumMap<>(Settings.class);
    private EnumMap<Settings, Boolean> mRestoredSettings = new EnumMap<>(Settings.class);

    private final File mLogsDir;
    private final Map<String, Date> mFileDateMap;
    private SimpleDateFormat mFildDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
    private SimpleDateFormat mMessageDateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

    private enum Settings
    {
        CHANNELS("Channel"),
        SYSTEMS("System"),
        CHARACTERS("Character");

        private String mNewId;

        Settings(String newId)
        {
            mNewId = newId;
        }

        public String getNewId()
        {
            return mNewId;
        }
    }

    public Jintel(String[] args) throws InterruptedException
    {
        if(args.length != 1)
            throw new IllegalArgumentException("Must supply logs directory as first argument.");

        mLogsDir = new File(args[0]);

        if(!mLogsDir.isDirectory())
            throw new IllegalArgumentException("Argument "+args[0]+" is not a directory.");

        mFileDateMap = new HashMap<>();

        //Initialize JFX:
        final CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                new JFXPanel(); //Init JFX environment.
                latch.countDown();
            }
        });
        latch.await();

        Platform.runLater(new Runnable()
        {
            @Override
            public void run()
            {

                MigPane pane = new MigPane("fill, insets 3 3 3 3");

                try
                {
                    new Text("Characters");
                } catch (Exception e)
                {

                }

                for(Settings settings : Settings.values())
                    addSettingsPanel(settings, pane);

                loadSettings();

                pane.setMinSize(250, 500);

                Scene scene = new Scene(pane);
                Stage stage = new Stage();
                stage.setScene(scene);

                stage.show();
                stage.setOnCloseRequest(new EventHandler<WindowEvent>()
                {
                    @Override
                    public void handle(WindowEvent windowEvent)
                    {
                        System.exit(0);
                    }
                });

                EventQueue.invokeLater(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        monitor();
                    }
                });

            }
        });
    }

    private void addSettingsPanel(final Settings settings, MigPane pane)
    {
        String name = settings.toString().toLowerCase();
        name = name.substring(0,1).toUpperCase().concat(name.substring(1));
        pane.add(new Text(name), "grow");
        Button add = new Button("+");
        add.setOnAction(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent actionEvent)
            {
                String[] settingsArray = new String[getSettingsValues(settings).size() + 1];
                getSettingsValues(settings).toArray(settingsArray);
                settingsArray[settingsArray.length - 1] = "New " + settings.getNewId();
                setSettingsValues(settings, Arrays.asList(settingsArray));
            }
        });
        pane.add(add, "wrap, align right");
        pane.add(getListView(settings), "grow, wrap, span");
    }


    private void parse(String message)
    {
        for(String character : getSettingsValues(Settings.CHARACTERS))
            if(message.contains(character))
                playSound();

        for(String system : getSettingsValues(Settings.SYSTEMS))
            if(message.contains(system))
                playSound();
    }

    public void playSound() {
        try {
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new File("resources/tos-redalert.wav").getAbsoluteFile());
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            clip.start();
        } catch(Exception ex) {
            System.out.println("Error with playing sound.");
            ex.printStackTrace();
        }
    }

    private boolean loadSettings()
    {
        for(Settings setting : Settings.values())
            mRestoredSettings.put(setting, false);

        File settings = new File("settings.jin");

        if (settingsFileIsInvalid(settings)) return false;

        try (BufferedReader reader = new BufferedReader(new FileReader(settings)))
        {
            String settingString = reader.readLine();
            while(settingString != null)
            {
                String[] keyValue = settingString.split("=");
                String[] split = keyValue.length > 1 ? keyValue[1].split(";") : new String[]{};
                Settings setting = Settings.valueOf(keyValue[0]);
                setSettingsValues(setting, Arrays.asList(split));
                mRestoredSettings.put(setting, true);
                settingString = reader.readLine();
            }

            addMissingSettings();

        } catch (Exception e)
        {
            e.printStackTrace();
            showError("Error reading settings file.");
            return false;
        }

        return true;
    }

    private boolean settingsFileIsInvalid(File settings)
    {
        if (!settings.isFile())
        {
            try
            {
                if (!settings.createNewFile())
                {
                    showError("Could not create settings file.");
                    return true;
                } else
                {
                    addMissingSettings();
                    playSound();
                }
            } catch (IOException e)
            {
                showError("Could not create settings file.");
                return true;
            }
        }
        return false;
    }

    private void addMissingSettings()
    {
        try (FileWriter writer = new FileWriter("settings.jin", true))
        {
            for (Settings s : Settings.values())
            {
                if (!mRestoredSettings.get(s))
                {
                    writer.write(s + "=NONE" + System.getProperty("line.separator"));
                }
            }
            writer.flush();
            writer.close();
        } catch (IOException e)
        {
            showError("Failed to add missing settings.");
        }
    }

    private void showError(String error)
    {
        System.out.println(error);
    }

    private void monitor()
    {
        while(true)
        {
            try
            {
                Thread.sleep(1000);

                for(File monitored : getMonitoredFiles())
                        getNewMessages(monitored);

            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

    }

    private List<File> getMonitoredFiles()
    {
        File[] files = mLogsDir.listFiles();

        Map<String, FileData> mLatest = new HashMap<>();

        for(File f : files)
        {
            try
            {
                String fileName = f.getName();
                int split = fileName.indexOf("_");

                if(split == -1)
                    continue;

                String fileKey = fileName.substring(0, split);

                if (getSettingsValues(Settings.CHANNELS).contains(fileKey))
                {
                    Date date = mFildDateFormat.parse(fileName.substring(split+1).replace(".txt",""));

                    if (mLatest.containsKey(fileKey))
                    {
                        Date existing = mLatest.get(fileKey).mDate;
                        if(date.after(existing))
                        {
                            mLatest.put(fileKey, new FileData(f, date));
                        }
                    }
                    else
                    {
                        mLatest.put(fileKey, new FileData(f, date));
                    }
                }
            } catch (ParseException e)
            {
                e.printStackTrace();
            }
        }

        List<File> monitored = new ArrayList<>();

        for(FileData f : mLatest.values()){
            monitored.add(f.mFile);
        }

        return monitored;
    }

    private Date parseDate(String e)
    {
        Matcher matcher = mMessageTimePattern.matcher(e);
        if(matcher.matches())
        {
            try
            {
                Date messageDate = mMessageDateFormat.parse(e.substring(2, e.indexOf("]")-1));
                return messageDate;
            } catch (ParseException e1)
            {
                e1.printStackTrace();
            }

        }
        return null;
    }

    private List<String> getNewMessages(File file)
    {
        try
        {
            RandomAccessFile handler = new RandomAccessFile(file, "r");
            long length = handler.length() - 1;
            StringBuilder sbr = new StringBuilder();

            List<String> messages = new ArrayList<>();
            Map<File, Date> updateMap = new HashMap<>();

            out : for(long pointer = length; pointer != -1; pointer--){
                handler.seek(pointer);
                int read = handler.readByte();

                boolean end = false;
                if(read == 0xA){
                    if(pointer < length)
                        end = true;
                } else if(read == 0xD){
                    if(pointer < length-1)
                        end = true;
                }
                sbr.append((char) read);

                if(end)
                {
                    String next = sbr.reverse().toString();
                    sbr = new StringBuilder();
                    next = next.replaceAll("[^\\p{Print}]+", "");
                    if(!next.equals(""))
                    {
                        messages.add(next);
                        Date messageDate = parseDate(next);

                        Date lastDate = mFileDateMap.get(file.getName());

                        if(lastDate == null)
                        {
                            //Record, ignore 1st last message.
                            mFileDateMap.put(file.getName(), messageDate);
                            break out;
                        } else if (messageDate.after(mFileDateMap.get(file.getName())))
                        {
                            //It's a new message, parse it.
                            parse(next);

                            if(updateMap.containsKey(file))
                            {
                                if(updateMap.get(file).before(messageDate))
                                    updateMap.put(file, messageDate);
                            } else {
                                updateMap.put(file, messageDate);
                            }
                        } else {
                            //Done.
                            break out;
                        }
                    }
                }
            }

            for(File key : updateMap.keySet())
            {
                mFileDateMap.put(key.getName(), updateMap.get(key));
            }

        } catch (FileNotFoundException e)
        {
            e.printStackTrace();
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public void setSettingsValues(Settings setting, List<String> values)
    {
        getListView(setting).setItems(FXCollections.observableList(values));
        getListView(setting).getSelectionModel().select(values.size()-1);
    }

    public ObservableList<String> getSettingsValues(Settings setting){ return getListView(setting).getItems(); }

    public ListView<String> getListView(Settings setting)
    {
        if(mSettingsListViews.get(setting) == null)
        {
            ListView<String> view = new ListView<>();
            setupListView(view, setting);
            mSettingsListViews.put(setting, view);
        }
        return mSettingsListViews.get(setting);
    }

    private void setupListView(final ListView<String> view, final Settings setting)
    {
        view.setEditable(true);

        view.setCellFactory(TextFieldListCell.forListView());
        view.setOnEditCommit(new EventHandler<ListView.EditEvent<String>>()
        {
            @Override
            public void handle(ListView.EditEvent<String> stringEditEvent)
            {
                view.getItems().set(stringEditEvent.getIndex(), stringEditEvent.getNewValue());
                persistSettings();
            }
        });

        ContextMenu contextMenu = new ContextMenu();
        view.setContextMenu(contextMenu);

        MenuItem remove = new MenuItem("Remove Selected");
        contextMenu.getItems().add(remove);

        remove.setOnAction(new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent actionEvent)
            {
                ObservableList<String> selectedItems = view.getSelectionModel().getSelectedItems();
                ObservableList<String> settingsValues = getSettingsValues(setting);

                ObservableList<String> diff = FXCollections.observableArrayList();
                diff.addAll(settingsValues);
                diff.removeAll(selectedItems);

                List<String> modified = new ArrayList<>();
                modified.addAll(diff);

                setSettingsValues(setting, modified);
                persistSettings();
            }
        });
    }

    private void persistSettings()
    {
        File settings = new File("settings.jin");
        if (settingsFileIsInvalid(settings))
        {
            showError("Failed to persist settings.");
            return;
        }

        try(BufferedWriter writer = new BufferedWriter(new FileWriter(settings));)
        {
            StringBuilder sbr = new StringBuilder();
            for(Settings setting : Settings.values())
            {
                sbr.append(setting.toString()+"=");
                for (String s : getListView(setting).itemsProperty().getValue())
                    sbr.append(s+";");
                sbr.append(System.getProperty("line.separator"));
            }

            writer.write(sbr.toString());
            writer.flush();
            writer.close();
        } catch (IOException e)
        {
            showError("Failed to persist settings.");
        }

        loadSettings();

    }

    public static void main(String[] args) throws InterruptedException
    {
        Jintel jintel = new Jintel(args);
    }

    private class FileData{
        File mFile;
        Date mDate;

        public FileData(File f, Date date)
        {
            mFile = f;
            mDate = date;
        }
    }

}
