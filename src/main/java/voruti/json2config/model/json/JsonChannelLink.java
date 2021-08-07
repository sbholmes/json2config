package voruti.json2config.model.json;

import lombok.Getter;
import voruti.json2config.model.IConvertible;

import java.util.List;

@SuppressWarnings("unused")
@Getter
public class JsonChannelLink implements IConvertible {

    private Value value;


    @Override
    public String toConfigLine(String lineBefore) {
        String format = "channel=\"%s\"}";
        if (lineBefore.endsWith("}")) {
            lineBefore = lineBefore.substring(0, lineBefore.length() - 1);
            format = "%s, " + format;
        } else {
            format = "%s {" + format;
        }

        return String.format(format, lineBefore, String.join(":", value.channelUID.segments)).strip();
    }


    @Getter
    public static class Value {
        private ChannelUID channelUID;
        private Configuration configuration;
        private String itemName;


        private static class ChannelUID {
            private List<String> segments;
        }

        private static class Configuration {
            private Properties properties;


            private static class Properties {
                private String offset;
                private String sourceFormat;
                private String profile;
                private String function;
            }
        }
    }
}