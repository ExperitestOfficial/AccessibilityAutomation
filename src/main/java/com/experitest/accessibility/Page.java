package com.experitest.accessibility;

import com.experitest.accessibility.colorthief.ContrastChecker;
import com.experitest.accessibility.colorthief.ContrastResult;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class Page {
    private List<Element> elementsList = new ArrayList<>();

    private List<Section> sections = new ArrayList<>();

    public List<Element> getElementsList() {
        return elementsList;
    }

    public List<Section> getSections() {
        return sections;
    }
    private Issue.Type[] validations = null;
    public HashSet<Issue.Type> getValidationsSet(){
        HashSet<Issue.Type> set = new HashSet<>();
        if(validations != null){
            Collections.addAll(set, validations);
        }
        return set;
    }

    /**
     * perform different validations
     * @param types the type of validations to perform
     * @throws IOException in case some IO communication fail
     */
    public void validate(Issue.Type ...types) throws IOException {
        this.validations = types;
        for(Issue.Type type: types){
            switch (type){
                case SIZE_TOO_SMALL_WIDTH:
                    for(Element element: getElementsList()){
                        if(element.getW() < Defaults.getInstance().getMinWidth()){
                            Issue issue = new Issue();
                            issue.setType(Issue.Type.SIZE_TOO_SMALL_WIDTH);
                            issue.setMessage("Element width is " + element.getW() + "px, minimum width should be 48px");
                            element.getIssues().add(issue);
                        }
                    }
                    break;
                case SIZE_TOO_SMALL_HEIGHT:
                    for(Element element: getElementsList()){
                        if(element.getH() < Defaults.getInstance().getMinHeight()){
                            Issue issue = new Issue();
                            issue.setType(Issue.Type.SIZE_TOO_SMALL_HEIGHT);
                            issue.setMessage("Element height is " + element.getH() + "px, minimum height should be 48px");
                            element.getIssues().add(issue);
                        }
                    }
                    break;
                case NO_ACCESSIBILITY_INFO:
                    for(Element element: getElementsList()){
                        boolean noLabel = element.getLabel() == null || element.getLabel().isEmpty();
                        boolean textNotFound = element.getText() != null && !element.getText().isEmpty() && !Arrays.toString(element.voiceOver()).contains(element.getText());
                        if(noLabel || textNotFound){
                            Issue issue = new Issue();
                            issue.setType(Issue.Type.NO_ACCESSIBILITY_INFO);
                            if(noLabel){
                                issue.setMessage("Element doesn't have label");
                            } else {
                                issue.setMessage("Following text: " + element.getText() + " cannot be found by the screen reader");
                            }
                        }
                    }
                    break;
                case CONTRAST:
                    for(Section section: sections){
                        for(Element element: section.getElements()){
                            if(element.getW() > 0 && element.getH() > 0 && section.getImage() != null){
                                try {
                                    ContrastResult result = generateContrast(element, section.getImage());
                                    if(result == null){
                                        continue;
                                    }

                                    double contrast = Defaults.getInstance().getSmallTextContrast();
                                    if(element.getH() > Defaults.getInstance().getContrastBreak()){
                                        contrast = Defaults.getInstance().getLargeTextContrast();
                                    }

                                    if((result.getRatio() < contrast && section.getImage2() != null)){
                                        result = generateContrast(element, section.getImage2());
                                    }
                                    if(result.getRatio() < contrast){
                                        Issue issue = new Issue();
                                        issue.setType(Issue.Type.CONTRAST);
                                        issue.setMessage("Contrast is too low: " + (Math.round(result.getRatio() * 100)/100) + ", expected " + contrast);
                                        element.getIssues().add(issue);
                                        ImageIO.write(result.getImage(), "PNG", new File("results", "out_" + System.currentTimeMillis() + ".png"));
                                    }
                                } catch (Exception ex) {
                                    System.err.println(Arrays.toString(element.voiceOver()));
                                    ex.printStackTrace();
                                }
                            }
                        }
                    }
                    break;
                case EXPECTED_CONTENT:
                    ContentAnalysis contentAnalysis = new ContentAnalysis(this);
                    contentAnalysis.verifyKey();
                    try {
                        contentAnalysis.initSections();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        continue;
                    }
                    contentAnalysis.process();
                    break;
            }
        }
    }

    private ContrastResult generateContrast(Element element, BufferedImage image){
        Rectangle elementRec = new Rectangle(element.getX(), element.getY(), element.getW(), element.getH());
        if(element.getInternalTextRec() != null){
            elementRec = element.getInternalTextRec();
        }
        Rectangle screenRec = new Rectangle(0, 0, image.getWidth(), image.getHeight());
        Rectangle elementNewRec = screenRec.intersection(elementRec);
        if(elementNewRec.width <= 0 || elementNewRec.height <= 0){
            return null;
        }
        BufferedImage newImage = new BufferedImage(elementNewRec.width, elementNewRec.height, image.getType());
        Graphics2D g = newImage.createGraphics();
        try {
            g.drawImage(image.getSubimage(elementNewRec.x, elementNewRec.y, elementNewRec.width, elementNewRec.height), 0, 0, null);
        } catch (Exception e){
            e.printStackTrace();
        }
        finally {
            g.dispose();
        }
//                                    BufferedImage image = section.getImage().getSubimage(x, y, element.getW(), element.getH());
        return ContrastChecker.findContrast(newImage);
    }
}
