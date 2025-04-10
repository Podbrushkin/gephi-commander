package importer;

import org.gephi.io.importer.api.FileType;
import org.gephi.io.importer.spi.FileImporter;
import org.gephi.io.importer.spi.FileImporterBuilder;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = FileImporterBuilder.class)
public class ImporterBuilderJSON implements FileImporterBuilder {

    public static final String IDENTIFER = "json";

    @Override
    public FileImporter buildImporter() {
        return new ImporterJSON();
    }

    @Override
    public String getName() {
        return IDENTIFER;
    }

    @Override
    public FileType[] getFileTypes() {
        FileType ft = new FileType(".json", NbBundle.getMessage(getClass(), "fileType_JSON_Name"));
        return new FileType[] {ft};
    }

    @Override
    public boolean isMatchingImporter(FileObject fileObject) {
        return fileObject.getExt().equalsIgnoreCase("json");
    }
}