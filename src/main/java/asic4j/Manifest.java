/**
 * Copyright (c) 2017 Martin Paljak
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package asic4j;

import org.cdoc4j.XML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Manifest {
    public static final String MANIFEST_XML = "META-INF/manifest.xml";
    ArrayList<String> errors = new ArrayList<>();
    private String mimetype; // mimetype of the package
    private ArrayList<ManifestEntry> files = new ArrayList<>(); // files listed in the manifest

    private Manifest(String mimetype) {
        this.mimetype = mimetype;
    }

    public static Manifest fromStream(InputStream in, String assumedMime) throws IOException {
        ArrayList<String> errors = new ArrayList<>();
        String packageMimeType = null;
        Document mf = XML.stream2dom(in);

        // TODO: possibly check against (RNG) schema?

        // Check version. We expect 1.2
        String version = mf.getDocumentElement().getAttribute("manifest:version");
        if (version == null || version.equalsIgnoreCase("")) {
            errors.add("Error: No manifest:version!");
        } else if (!version.equalsIgnoreCase("1.2")) {
            errors.add("Error: manifest:version != 1.2");
        }

        ArrayList<ManifestEntry> files = new ArrayList<>();
        //NodeList files = mf.getDocumentElement().getChildNodes();
        for (Node n : XML.asList(mf.getDocumentElement().getElementsByTagName("manifest:file-entry"))) {
            String fullPath = n.getAttributes().getNamedItem("manifest:full-path").getTextContent();
            String mediaType = n.getAttributes().getNamedItem("manifest:media-type").getTextContent();
            long fileSize = -1;
            // Ignore package entry in manifest
            if (fullPath.equals("/")) {
                packageMimeType = mediaType;
                // But check the mimetype
                if (assumedMime != null) {
                    if (!mediaType.equals(assumedMime)) {
                        errors.add("Error: mime type does not match expected: " + assumedMime + " vs " + mediaType);
                    }
                }
            } else {
                // File size is optional
                Node filesize = n.getAttributes().getNamedItem("manifest:size");
                if (filesize != null) {
                    // Add to container
                    fileSize = Long.parseLong(filesize.getTextContent());
                }
                ManifestEntry mfile = new ManifestEntry(fullPath, mediaType, fileSize);
                files.add(mfile);
            }
        }
        // Construct manifest.
        Manifest manifest = new Manifest(packageMimeType);
        for (ManifestEntry f : files) {
            manifest.addFile(f);
        }
        manifest.errors = errors;
        return manifest;
    }

    public static Manifest create(String mimetype) {
        return new Manifest(mimetype);
    }

    public List<String> getErrors() {
        return this.errors;
    }

    public String getMimeType() {
        return mimetype;
    }

    public void setFileSize(String name, long size) {
        for (ManifestEntry e : files) {
            if (e.path.equals(name)) {
                e.size = size;
                return;
            }
        }
        throw new IllegalArgumentException(name + " not found");
    }

    public List<ManifestEntry> getFiles() {
        return Collections.unmodifiableList(files);
    }

    public Manifest addFile(String path, String mimetype, long size) {
        ManifestEntry mf = new ManifestEntry(path, mimetype, size);
        files.add(mf);
        return this;
    }

    public Manifest addFile(ManifestEntry f) {
        files.add(f);
        return this;
    }

    // Writes the constructed manifest as XML to the specified stream.
    public void toStream(OutputStream out) {
        // Construct the XML.
        Document manifest = XML.getDocument();
        Element root = manifest.createElement("manifest:manifest");
        root.setAttribute("xmlns:manifest", "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0");
        root.setAttribute("manifest:version", "1.2");
        manifest.appendChild(root);

        // Add package, if mimetype present
        if (mimetype != null) {
            Element pkg = manifest.createElement("manifest:file-entry");
            pkg.setAttribute("manifest:full-path", "/");
            pkg.setAttribute("manifest:media-type", this.mimetype);
            root.appendChild(pkg);
        }
        // Add all files
        for (ManifestEntry f : files) {
            // TODO: conditional ignoring of META-INF?
            if (f.path.startsWith("META-INF/")) {
                continue;
            }
            Element file = manifest.createElement("manifest:file-entry");
            file.setAttribute("manifest:full-path", f.path);
            file.setAttribute("manifest:media-type", f.mimetype);
            if (f.size != -1) {
                file.setAttribute("manifest:size", Long.toUnsignedString(f.size));
            }
            root.appendChild(file);
        }
        XML.dom2stream(manifest, out);
    }
}
