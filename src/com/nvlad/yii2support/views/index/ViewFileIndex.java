package com.nvlad.yii2support.views.index;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.jetbrains.php.lang.PhpFileType;
import com.nvlad.yii2support.utils.Yii2SupportSettings;
import com.nvlad.yii2support.views.ViewUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ViewFileIndex extends FileBasedIndexExtension<String, ViewInfo> {
    public static final ID<String, ViewInfo> identity = ID.create("Yii2Support.ViewFileIndex");
    private final ViewDataIndexer myViewDataIndexer;
    private final ViewInfoDataExternalizer myViewInfoDataExternalizer;
    private final FileBasedIndex.InputFilter myInputFilter;

    public ViewFileIndex() {
        myViewDataIndexer = new ViewDataIndexer();
        myViewInfoDataExternalizer = new ViewInfoDataExternalizer();
        myInputFilter = new ViewFileInputFilter(PhpFileType.INSTANCE);
    }

    @NotNull
    @Override
    public ID<String, ViewInfo> getName() {
        return identity;
    }

    @NotNull
    @Override
    public DataIndexer<String, ViewInfo, FileContent> getIndexer() {
        return myViewDataIndexer;
    }

    @NotNull
    @Override
    public KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @NotNull
    @Override
    public DataExternalizer<ViewInfo> getValueExternalizer() {
        return myViewInfoDataExternalizer;
    }

    @Override
    public int getVersion() {
        return 9;
    }

    @NotNull
    @Override
    public FileBasedIndex.InputFilter getInputFilter() {
        System.out.println("FileBasedIndex.InputFilter getInputFilter()");
        return myInputFilter;
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }

    private static class ViewDataIndexer implements DataIndexer<String, ViewInfo, FileContent> {
        static final Map<Project, Map<Pattern, String>> projectViewPatterns = new HashMap<>();
        static final VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();

        @Override
        @NotNull
        public Map<String, ViewInfo> map(@NotNull final FileContent inputData) {
            final Project project = inputData.getProject();
            Map<Pattern, String> patterns = projectViewPatterns.get(project);
            if (patterns == null) {
                patterns = new HashMap<>();
                Yii2SupportSettings settings = Yii2SupportSettings.getInstance(project);
                for (Map.Entry<String, String> entry : settings.viewPathMap.entrySet()) {
                    String patternString = "^(" + entry.getKey().replace("*", "[\\w-]+") + ").+";
                    Pattern pattern = Pattern.compile(patternString);
                    patterns.put(pattern, entry.getValue());
                }
                projectViewPatterns.put(project, patterns);
            }

            final String projectPath = project.getBaseDir().getPath();
            int projectBaseDirLength = projectPath.length();
            final String absolutePath = inputData.getFile().getPath();
            if (!absolutePath.startsWith(projectPath)) {
                return Collections.emptyMap();
            }

            String path = absolutePath.substring(projectBaseDirLength);
            if (!path.startsWith("/vendor/")) {
                String application = "basic";

                if (virtualFileManager.findFileByUrl(project.getBaseDir().getUrl() + "/web") == null) {
                    int applicationNameEnd = path.indexOf("/", 1);
                    if (applicationNameEnd != -1) {
                        application = path.substring(1, applicationNameEnd);
                        path = path.substring(applicationNameEnd);
                    }
                }

                path = "@app" + path;
                if (!path.startsWith("@app/views/")) {
                    String viewPath = null;
                    for (Map.Entry<Pattern, String> entry : patterns.entrySet()) {
                        Matcher matcher = entry.getKey().matcher(path);
                        if (matcher.find()) {
                            viewPath = entry.getValue() + path.substring(matcher.end(1));
                            break;
                        }
                    }
                    if (viewPath == null) {
                        return Collections.emptyMap();
                    }
                    path = viewPath;
                }
                if (inputData.getFile().getExtension() != null) {
                    path = path.substring(0, path.length() - inputData.getFile().getExtension().length() - 1);
                }

                System.out.println("ViewDataIndexer.map > " + absolutePath + " => " + path);

                Map<String, ViewInfo> map = new HashMap<>();
                ViewInfo viewInfo = new ViewInfo(inputData);
                viewInfo.application = application;
                viewInfo.parameters = ViewUtil.getPhpViewVariables(inputData.getPsiFile());

                map.put(path, viewInfo);
                return map;
            }

            return Collections.emptyMap();
        }
    }

    private static class ViewInfoDataExternalizer implements DataExternalizer<ViewInfo> {
        @Override
        public void save(@NotNull DataOutput dataOutput, ViewInfo viewInfo) throws IOException {
            System.out.println("ViewInfoDataExternalizer.save ==> " + viewInfo.fileUrl);

            writeString(dataOutput, viewInfo.fileUrl);
            writeString(dataOutput, viewInfo.application);
            dataOutput.writeInt(viewInfo.parameters.size());
            for (String parameter : viewInfo.parameters) {
                writeString(dataOutput, parameter);
            }
        }

        @Override
        public ViewInfo read(@NotNull DataInput dataInput) throws IOException {
            ViewInfo viewInfo = new ViewInfo();
            viewInfo.fileUrl = readString(dataInput);
            viewInfo.application = readString(dataInput);
            final int parameterCount = dataInput.readInt();
            viewInfo.parameters = new ArrayList<>(parameterCount);
            for (int i = 0; i < parameterCount; i++) {
                viewInfo.parameters.add(readString(dataInput));
            }

            System.out.println("ViewInfoDataExternalizer.read <== " + viewInfo.fileUrl);
            return viewInfo;
        }

        private void writeString(DataOutput dataOutput, String data) throws IOException {
            dataOutput.writeInt(data.length());
            dataOutput.writeChars(data);
        }

        private String readString(DataInput dataInput) throws IOException {
            final int length = dataInput.readInt();
            if (length == 0) {
                return "";
            }
            char[] chars = new char[length];
            for (int i = 0; i < length; i++) {
                chars[i] = dataInput.readChar();
            }
            return new String(chars);
        }
    }

    private class ViewFileInputFilter implements FileBasedIndex.InputFilter {
        private final FileType myFileType;

        ViewFileInputFilter(FileType fileType) {
            this.myFileType = fileType;
        }

        @Override
        public boolean acceptInput(@NotNull VirtualFile virtualFile) {
            return virtualFile.getFileType() == myFileType;
        }
    }
}