package voruti.json2config.service;

import lombok.extern.slf4j.Slf4j;
import voruti.json2config.model.json.JsonChannelLink;

import java.io.File;
import java.io.IOException;
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
        log.debug("Starting ChannelAppender with channelLinkFile={}, directory={}", channelLinkFile, directory);

        try {
            // open file:
            String content = SharedService.openFileToString(channelLinkFile);
            // map to list of channel links:
            List<JsonChannelLink> channelLinkList = SharedService.jsonToConvertibleMap(content, Type.CHANNEL).values().stream()
                    .map(JsonChannelLink.class::cast)
                    .collect(Collectors.toList());
            log.info("Found {} channel links", channelLinkList.size());
            log.trace("channelLinkList={}", channelLinkList);

            // search items files:
            List<String> itemsFiles = findItemsFilesInDir(directory);
            // get names of all items:
            List<String> itemNamesList = itemsFiles.stream()
                    .map(ChannelAppender::getItemNamesFromFile)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
            log.info("Found {} items", itemNamesList.size());
            log.trace("itemNamesList={}", itemNamesList);

            // only channels present in both lists:
            List<JsonChannelLink> relevantChannelLinkList = channelLinkList.stream()
                    .filter(channelLink -> itemNamesList.stream()
                            .anyMatch(itemName -> itemName.equals(channelLink.getValue().getItemName())))
                    .collect(Collectors.toList());
            log.info("{} match with each other", relevantChannelLinkList.size());
            log.trace("relevantChannelLinkList={}", relevantChannelLinkList);

            int count = 0;
            for (JsonChannelLink channel : relevantChannelLinkList) {
                for (String iFile : itemsFiles) {
                    if (setChannelToItemInFile(channel, iFile)) {
                        count++;
                    }
                }
            }
            log.info("Successfully appended {} channel links!", count);

            log.warn("Warning: You might need to manually fix some converting mistakes (double channels, etc.)");
        } catch (IOException e) {
            log.error(Constants.LOG_CANT_OPEN_FILE, channelLinkFile);
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
        try {
            return Arrays.stream(SharedService.openFileToString(fileName).split("\n"))
                    .filter(line -> !line.isEmpty() && !line.toLowerCase().startsWith("group"))
                    .map(ChannelAppender::searchNameInLine)
                    .filter(itemName -> !itemName.isEmpty())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error(Constants.LOG_CANT_OPEN_FILE, fileName);
        }

        return List.of();
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
                            if (!readItemName.isEmpty() && readItemName.equals(channelLink.getValue().getItemName())) {
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
            log.error(Constants.LOG_CANT_OPEN_FILE, fileName);
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
        String[] arr = line.strip().split("\\s+");

        String itemName = "";
        if (arr.length >= 2) {
            itemName = arr[1];
        }

        return itemName;
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