/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dotaSoundEditor.Controls;

import dotaSoundEditor.Helpers.SoundPlayer;
import dotaSoundEditor.Helpers.ScriptParser;
import dotaSoundEditor.Helpers.Utility;
import dotaSoundEditor.*;
import info.ata4.vpk.VPKArchive;
import info.ata4.vpk.VPKEntry;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author
 * Image
 * 17
 */
public abstract class EditorPanel extends JPanel
{

    protected TreeModel currentTreeModel;
    protected SoundPlayer currentSound = SoundPlayer.getInstance();
    protected JTree currentTree;
    protected JComboBox currentDropdown;
    protected String vpkDir;
    protected String installDir;

    abstract void populateSoundListAsTree();
    abstract void fillImageFrame(Object selectedItem) throws IOException;
    abstract void revertButtonActionPerformed(java.awt.event.ActionEvent evt);
    abstract void playSoundButtonActionPerformed(java.awt.event.ActionEvent evt);
    abstract void revertAllButtonActionPerformed(java.awt.event.ActionEvent evt);
    abstract void replaceButtonActionPerformed(java.awt.event.ActionEvent evt);
    abstract void advancedButtonActionPerformed(java.awt.event.ActionEvent evt, JButton advancedButton);
    abstract void populateDropdownBox();
    abstract String getCurrentScriptString();
    abstract String getCustomSoundPathString();                    
    
    protected final File promptUserForNewFile(String wavePath)
    {
        JFileChooser chooser = new JFileChooser(new File(UserPrefs.getInstance().getWorkingDirectory()));
        FileNameExtensionFilter filter = new FileNameExtensionFilter("MP3s and WAVs", "mp3", "wav");
        chooser.setFileFilter(filter);

        int chooserRetVal = chooser.showOpenDialog(chooser);
        if (chooserRetVal == JFileChooser.APPROVE_OPTION)
        {
            DefaultMutableTreeNode selectedFile = (DefaultMutableTreeNode) getTreeNodeFromWavePath(wavePath);
            Path chosenFile = Paths.get(chooser.getSelectedFile().getAbsolutePath());
            Path destPath = Paths.get(installDir + "\\dota\\sound\\" + getCustomSoundPathString() + chosenFile.getFileName());
            UserPrefs.getInstance().setWorkingDirectory(chosenFile.getParent().toString());

            try
            {
                boolean success = new File(destPath.toString()).mkdirs();
                Files.copy(chosenFile, destPath, StandardCopyOption.REPLACE_EXISTING);

                String waveString = selectedFile.getUserObject().toString();
                int startIndex = -1;
                int endIndex = -1;
                if (waveString.contains("\"wave\""))
                {
                    startIndex = Utility.nthOccurrence(selectedFile.getUserObject().toString(), '\"', 2);
                    endIndex = Utility.nthOccurrence(selectedFile.getUserObject().toString(), '\"', 3);
                }
                else    //Some wavestrings don't have the "wave" at the beginning for some reason
                {
                    startIndex = Utility.nthOccurrence(selectedFile.getUserObject().toString(), '\"', 0);
                    endIndex = Utility.nthOccurrence(selectedFile.getUserObject().toString(), '\"', 1);
                }

                String waveSubstring = waveString.substring(startIndex, endIndex + 1);
                waveString = waveString.replace(waveSubstring, "\"" + getCustomSoundPathString() + chosenFile.getFileName() + "\"");
                selectedFile.setUserObject(waveString);

                //Write out modified tree to scriptfile.
                ScriptParser parser = new ScriptParser(this.currentTreeModel);
                String scriptString = getCurrentScriptString();
                Path scriptPath = Paths.get(scriptString);
                parser.writeModelToFile(scriptPath.toString());

                //Update UI
                populateSoundListAsTree();
                JOptionPane.showMessageDialog(this, "Sound file successfully replaced.");

            }
            catch (IOException ex)
            {
                System.err.println(ex);
            }
        }
        return null;
    }

    protected void attachDoubleClickListenerToTree()
    {
        MouseListener ml = new MouseAdapter()
        {
            public void mousePressed(MouseEvent e)
            {
                int selRow = currentTree.getRowForLocation(e.getX(), e.getY());
                TreePath selPath = currentTree.getPathForLocation(e.getX(), e.getY());
                if (selRow != -1 && ((DefaultMutableTreeNode) selPath.getLastPathComponent()).isLeaf())
                {
                    if (e.getClickCount() == 2)
                    {
                        playSelectedTreeSound(selPath);
                    }
                }
            }
        };
        currentTree.addMouseListener(ml);
    }

    protected boolean playSelectedTreeSound(TreePath selPath)
    {
        boolean regenScript = true;
        try
        {
            DefaultMutableTreeNode selectedFile = ((DefaultMutableTreeNode) selPath.getLastPathComponent());
            String waveString = selectedFile.getUserObject().toString();
            File soundFile = createSoundFileFromWaveString(waveString);
            if(soundFile == null)
            {
                return regenScript;
            }
            currentSound.loadSound(soundFile.getAbsolutePath());
            currentSound.playSound();
            regenScript = false;
            return regenScript;
        }
        catch (Exception ex)
        {
            JOptionPane.showMessageDialog(null, "The selected node does not represent a valid sound file.", "Error", JOptionPane.ERROR_MESSAGE);
            regenScript = false;
            return regenScript;
        }
    }

    protected ArrayList<String> getWavePathsAsList(TreeNode selectedFile)
    {
        ArrayList<String> wavePathsList = new ArrayList<String>();
        Enumeration e = selectedFile.children();
        while (e.hasMoreElements())
        {
            Object currentElement = e.nextElement();

            //If a soundfile has multiple possible wavefiles
            if (currentElement.toString().contains("\"rndwave\""))
            {
                Enumeration innerE = ((TreeNode) currentElement).children();
                while (innerE.hasMoreElements())
                {
                    Object currentInnerElement = innerE.nextElement();
                    if (currentInnerElement.toString().contains("\"wave\"") || currentInnerElement.toString().contains(".wav") || currentInnerElement.toString().contains(".mp3"))
                    {
                        //Maybe do some string massaging here before we just hand it back
                        wavePathsList.add(((TreeNode) currentInnerElement).toString());
                    }
                }
            }
            //If it only has one
            else if (currentElement.toString().contains("\"wave\""))
            {
                wavePathsList.add(((TreeNode) currentElement).toString());
            }
        }
        return wavePathsList;
    }

    private File createSoundFileFromWaveString(String waveString)
    {
        if (!(waveString.contains(".wav") || (waveString.contains(".mp3"))))
        {
            return null;
        }

        File file = new File(vpkDir);
        VPKArchive vpk = new VPKArchive();
        File entryFile = new File("");

        String waveSubstring = "";
        int startIndex = -1;
        int endIndex = -1;
        if (waveString.contains("\"wave\""))
        {
            startIndex = Utility.nthOccurrence(waveString, '\"', 2);
            endIndex = Utility.nthOccurrence(waveString, '\"', 3);
        }
        //For weird special cases where the "wave" part of the string is missing, i.e. Treant's Overgrowth.Target spell
        else
        {
            startIndex = Utility.nthOccurrence(waveString, '\"', 0);
            endIndex = Utility.nthOccurrence(waveString, '\"', 1);
        }

        waveSubstring = waveString.substring(startIndex, endIndex + 1);
        waveSubstring = waveSubstring.replace(")", "");
        waveSubstring = waveSubstring.replace("\"", "");
        waveSubstring = waveSubstring.replace("\\", "/");
        waveSubstring = waveSubstring.replace("#", "");
        waveSubstring = waveSubstring.replace("*", "");

        if (!waveString.contains("custom"))
        {
            File localFile = new File(Paths.get(installDir + "\\sound\\" + waveSubstring).toString());
            if (localFile.isFile())
            {
                return localFile;
            }

            try
            {
                vpk.load(file);
            }
            catch (Exception ex)
            {
                System.err.println("Can't open archive: " + ex.getMessage());
            }
            waveSubstring = "sound/" + waveSubstring;
            VPKEntry entry = vpk.getEntry(waveSubstring.toLowerCase());
            if (entry == null)
            {
                int result = JOptionPane.showConfirmDialog(this, "A default game sound cannot be found. "
                        + "This usually happens when Valve "
                        + "\nchanges the internal location of script files. It's likely that many "
                        + "\nentries for this script file are now out of date. "
                        + "\n\nWould you like to regenerate the script file? This "
                        + "will erase any changes you've made.", "Script File Out of Date", JOptionPane.YES_NO_OPTION);
                if(result == JOptionPane.YES_OPTION)
                {
                    return null;
                }
            }

            entryFile = entry.getType().contains("wav")
                    ? new File(Paths.get(System.getProperty("user.dir") + "\\scratch\\scratch.wav").toString())
                    : new File(Paths.get(System.getProperty("user.dir") + "\\scratch\\scratch.mp3").toString());

            try (FileChannel fc = FileUtils.openOutputStream(entryFile).getChannel())
            {
                fc.write(entry.getData());
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }

            return entryFile;
        }
        else    //If it's NOT stored in the VPK, it's on the local filesys
        {                        
            entryFile = new File(Paths.get(installDir + "\\dota\\sound\\" + waveSubstring).toString());
            return entryFile;
        }
    }

    protected TreeNode getTreeNodeFromWavePath(String wavePath)
    {
        TreeModel model = this.currentTreeModel;

        TreeNode root = (TreeNode) model.getRoot();
        for (Enumeration e = ((DefaultMutableTreeNode) root).breadthFirstEnumeration(); e.hasMoreElements() && root != null;)
        {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
            if (node.toString().contains(wavePath))
            {
                return node;
            }
        }
        return null;
    }

    protected void deleteSoundFileByWaveString(String selectedWaveString)
    {
        int startIndex = -1;
        int endIndex = -1;
        if (selectedWaveString.contains("\"wave\""))
        {
            startIndex = Utility.nthOccurrence(selectedWaveString, '\"', 2);
            endIndex = Utility.nthOccurrence(selectedWaveString, '\"', 3);
        }
        else
        {
            startIndex = Utility.nthOccurrence(selectedWaveString, '\"', 1);
            endIndex = Utility.nthOccurrence(selectedWaveString, '\"', 2);
        }

        String waveSubstring = selectedWaveString.substring(startIndex, endIndex + 1);
        waveSubstring = waveSubstring.replace(")", "");
        waveSubstring = waveSubstring.replace("\"", "");
        File soundFileToDelete = new File(Paths.get(installDir + "\\dota\\sound\\" + waveSubstring).toString());
        if (soundFileToDelete.isFile())
        {
            soundFileToDelete.delete();
        }
        else
        {
            System.err.println("Cannot find and delete custom sound file " + waveSubstring);
        }
    }

    protected ArrayList<String> getWavePathListFromString(String scriptString)
    {
        ArrayList<String> wavePathsList = new ArrayList<>();
        BufferedReader buf = new BufferedReader(new StringReader(scriptString));
        String line = null;
        try
        {
            while ((line = buf.readLine()) != null)
            {
                //Stop reading after we're done with the wave paths               
                if (line.contains("soundentry"))
                {
                    break;
                }

                if (line.contains("\"wave\"") || line.contains(".wav") || line.contains(".mp3"))
                {
                    wavePathsList.add(line);
                }
            }
            return wavePathsList;
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            return null;
        }
    }    
}