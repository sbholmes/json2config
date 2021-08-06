package voruti.json2config.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import voruti.json2config.model.json.JsonChannelLink;
import voruti.json2config.service.SharedService.Type;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author voruti
 */
@Slf4j
public class ChannelAppender {

    private static final Marker FATAL = MarkerFactory.getMarker("FATAL");


    private ChannelAppender() {
    }


    /**
     * Appends the channel links from {@code channelLinkFile} to all ".items" files
     * in the {@code directory}.
     *
     * @param channelLinkFile path to the file which contains the channel links in
     *                        JSON format
     * @param directory       the directory in which to search for ".items" files
     */
    public static void start(String channelLinkFile, String directory) {
        log.info("Starting ChannelAppender with channelLinkFile={}, directory={}", channelLinkFile, directory);

        try {
            // open file:
            String content = SharedService.openFileToString(channelLinkFile);
            // map to list of channel links:
            List<JsonChannelLink> channelsList = SharedService.jsonToConvertibleMap(content, Type.CHANNEL).values().stream()
                    .map(JsonChannelLink.class::cast)
                    .collect(Collectors.toList());
            log.trace("channelsList={} with size={}", channelsList, channelsList.size());
            System.out.printf("Found %s channel links.%n", channelsList.size());

            // search items files:
            List<String> itemsFiles = findItemsFilesInDir(directory);
            List<String> itemNamesList = itemsFiles.stream()
                    .map(ChannelAppender::getItemNamesFromFile)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
            log.trace("itemNamesList={} with size={}", itemNamesList, itemNamesList.size());
            System.out.printf("Found %s items.%n", itemNamesList.size());

            // only items present in both lists:
            List<String> newItemNamesList = itemNamesList.stream().filter(n -> {
                for (JsonChannelLink channel : channelsList) {
                    if (channel.getValue().getItemName().equals(n)) {
                        return true;
                    }
                }
                return false;
            }).collect(Collectors.toList());
            List<JsonChannelLink> relevantChannelsList = channelsList.stream()
                    .filter(c -> newItemNamesList.contains(c.getValue().getItemName())).collect(Collectors.toList());
            log.trace("relevantChannelsList={} with size={}",
                    relevantChannelsList, relevantChannelsList.size());
            System.out.printf("%s match with each other.%n", relevantChannelsList.size());

            int count = 0;
            for (JsonChannelLink channel : relevantChannelsList) {
                for (String iFile : itemsFiles) {
                    if (setChannelToItemInFile(channel, iFile))
                        count++;
                }
            }
            log.trace("Added count={} times", count);
            System.out.printf("Successfully appended %s channel links!%n", count);

            System.out.println("Warning: You might need to manually fix some converting mistakes (double channels, etc.)");
        } catch (IOException e) {
            log.error("Can't open file {}", channelLinkFile);
        }
    }

    /**
     * Returns a {@link List} of Strings containing the names of all items in
     * {@code fileName}.
     *
     * @param fileName the file(-Name) to open and search for items
     * @return a {@link List} containing the names of the items
     */
    public static List<String> getItemNamesFromFile(String fileName) {
        List<String> returnVal = new ArrayList<>();

        try {
            return Arrays.stream(SharedService.openFileToString(fileName).split("\n"))
                    .filter(line -> !line.isEmpty() && !line.toLowerCase().startsWith("group"))
                    .map(ChannelAppender::searchNameInLine)
                    .filter(itemName -> itemName != null && !itemName.isEmpty())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Can't open file {}", fileName);
        }

        return returnVal;
    }

    /**
     * Appends the {@code channelLink} after the item in {@code fileName}.
     *
     * @param channelLink the channel link to append after the item
     * @param fileName    the file to search for the item
     * @return {@code true} if the {@code channelLink} could be appended, {@code false} otherwise
     */
    public static boolean setChannelToItemInFile(JsonChannelLink channelLink, String fileName) {
        boolean successful = false;

        try {
            List<String> originalLines = Arrays.asList(SharedService.openFileToString(fileName).split("\n"));
            List<String> modifiedLines = originalLines.stream()
                    .map(line -> {
                        if (!line.isEmpty() && !line.toLowerCase().startsWith("group")) {
                            String readItemName = searchNameInLine(line);
                            if (readItemName != null && !readItemName.isEmpty()
                                    && readItemName.equals(channelLink.getValue().getItemName())) {
                                return channelLink.toConfigLine(line);
                            }
                        }
                        // return unmodified line:
                        return line;
                    })
                    .collect(Collectors.toList());

            if (!originalLines.equals(modifiedLines)) {
                successful = SharedService.writeLinesToFile(modifiedLines, fileName);
            }
        } catch (IOException e) {
            log.error("Can't open file {}", fileName);
        }

        return successful;
    }

    /**
     * Searches for an item name in the {@code line}.
     *
     * @param line the line to search in
     * @return name of the item if found, {@code null} otherwise
     */
    public static String searchNameInLine(String line) {
        String[] arr = line.trim().split("\\s+");

        String returnVal = null;
        if (arr.length >= 2) {
            returnVal = arr[1];
        }

        return returnVal;
    }

    /**
     * Searches for ".items" files in the {@code directory}.
     *
     * @param directory the directory to search in
     * @return a {@link List} with all file paths of ".items" files
     */
    public static List<String> findItemsFilesInDir(String directory) {
        return Arrays.stream(Objects.requireNonNull(new File(directory).listFiles((dir, filename) -> filename.endsWith(".items"))))
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());
    }
}
