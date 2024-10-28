import {H4, Icon, Spinner} from "@blueprintjs/core";
import {convertArtifactsByTypes, isPrintableArtifact} from "../common";
import React from "react";
import './style.css'

export default function artifactList({
                                         loadingArtifacts,
                                         artifacts,
                                         getDocument,
                                         selectedComponent,
                                         selectedMinor,
                                         selectedVersion,
                                         selectedDocument,
                                         adminMode,
                                         deleteArtifact,
                                         showConfirmation
                                     }) {
    if (loadingArtifacts) {
        return <div className='load-artifacts-list'>
            <Spinner size={50} intent="primary"/>
        </div>
    } else {
        const [printableArtifacts, binaryArtifacts, dockerImages] = convertArtifactsByTypes(artifacts)
        return <div className='artifacts-component-list-block'>
            {printableArtifacts.length > 0 &&
                <div className='box'>
                    <H4> Documents </H4>
                    {artifactBlock(printableArtifacts, getDocument, selectedComponent, selectedMinor, selectedVersion, selectedDocument, adminMode, deleteArtifact, showConfirmation)}
                </div>
            }
            {binaryArtifacts.length > 0 &&
                <div className='box'>
                    <H4> Binaries </H4>
                    {artifactBlock(binaryArtifacts, getDocument, selectedComponent, selectedMinor, selectedVersion, selectedDocument, adminMode, deleteArtifact, showConfirmation)}
                </div>
            }
            {dockerImages.length > 0 &&
                <div className='box'>
                    <H4> Docker images </H4>
                    {artifactBlock(dockerImages, getDocument, selectedComponent, selectedMinor, selectedVersion, selectedDocument, adminMode, deleteArtifact, showConfirmation, false)}
                </div>
            }
        </div>
    }
}

function ArtifactLabel({
                           getDocument,
                           selectedComponent,
                           selectedVersion,
                           selectedDocument,
                           id,
                           displayName,
                           fileName,
                           isPrintable,
                           isDeletable,
                           deleteArtifact,
                           showConfirmation,
                           isDownloadable = true,
                       }) {

    const isSelected = selectedDocument.id === id

    const handleOnCopyClick = () => {
        navigator.clipboard.writeText(`${fileName}`)
    }

    return <div
        className={`artifact-label-wrap ${isSelected ? 'selected' : ''}`}
    >
        {isPrintable
            ? <div className='artifact-label-link'
                   onClick={() => {
                       getDocument(selectedComponent, selectedVersion, id, isPrintable, displayName)
                   }}
            >{displayName}</div>
            : <div className='artifact-label-text'>{displayName}</div>
        }

        <div className='artifact-label-right'>
            {!isDownloadable &&
                <div role='button' onClick={handleOnCopyClick}>
                    <Icon icon='clipboard'/>
                </div>
            }
            {isDownloadable &&
                <a href={`rest/api/3/components/${selectedComponent}/versions/${selectedVersion}/artifacts/${id}/download`}
                   download={fileName}>
                    <Icon icon='import'/>
                </a>
            }
            {isPrintable &&
                <a href={`rest/api/3/components/${selectedComponent}/versions/${selectedVersion}/artifacts/${id}/download`}
                   target="_blank" hidden={!isPrintable}>
                    <Icon icon='share'/>
                </a>
            }
            {isDeletable &&
                <a href="#" onClick={() =>
                    showConfirmation(`Delete artifact ${selectedComponent}:${selectedVersion} ${displayName}?`,
                        () => deleteArtifact(selectedComponent, selectedVersion, id))
                }>
                    <Icon icon='trash' style={{color: "red"}}/>
                </a>
            }
        </div>
    </div>
}

function artifactBlock(artifacts,
                       getDocument,
                       selectedComponent,
                       selectedMinor,
                       selectedVersion,
                       selectedDocument,
                       adminMode,
                       deleteArtifact,
                       showConfirmation,
                       isDownloadable = true) {
    return artifacts.map(artifact => {
        const {fileName, id, displayName} = artifact
        return <ArtifactLabel
            key={id}
            displayName={displayName}
            id={id}
            getDocument={getDocument}
            selectedComponent={selectedComponent}
            selectedMinor={selectedMinor}
            selectedVersion={selectedVersion}
            selectedDocument={selectedDocument}
            fileName={fileName}
            isPrintable={isPrintableArtifact(artifact)}
            isDeletable={adminMode}
            deleteArtifact={deleteArtifact}
            showConfirmation={showConfirmation}
            isDownloadable={isDownloadable}
        />
    })
}
