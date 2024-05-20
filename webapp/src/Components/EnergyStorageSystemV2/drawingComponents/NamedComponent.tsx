export type LabelPlacement = 'down' | 'up';
export type NamedComponentProps = {
    x: number;
    y: number;
    mainColor: string;
    labelColor: string;
    numberOfHolesLeft: number;
    numberOfHolesRight: number;
    numberOfHolesHorizontal: number;
    labelPlacement: LabelPlacement;
}


export const NamedComponent = ({
                                   mainColor,
                                   labelColor,
                                   x,
                                   y,
                                   numberOfHolesLeft,
                                   numberOfHolesRight,
                                   numberOfHolesHorizontal,
                                   labelPlacement,
                               }: NamedComponentProps) => {

    const width = 150;
    const height = 133;
    const arc = 20;
    const labelExtraHeight = 5;

    let verticalStrLeft = calcVerticalHolesString(height, numberOfHolesLeft, false);
    let verticalStrRight = calcVerticalHolesString(height, numberOfHolesRight, true);
    let horizontalStringUp = labelPlacement === "up" ? `h ${width}` : calcHorizontalString(width, numberOfHolesHorizontal, false);
    let horizontalStringDown = labelPlacement === "down" ? `h -${width}` : calcHorizontalString(width, numberOfHolesHorizontal, true);

    return (<>
        <path d={`M ${x} ${y}
        ${horizontalStringUp}
        ${verticalStrLeft}
        ${horizontalStringDown}
        ${verticalStrRight}
        z`}
              stroke="transparent"
              fill={mainColor}
        />

        {labelPlacement === 'up' && <LabelUp
            x={x}
            y={y}
            arc={arc}
            width={width}
            extraHeight={labelExtraHeight}
            labelColor={labelColor}
        />}
        {labelPlacement === 'down' && <LabelDown
            x={x}
            y={y}
            arc={arc}
            width={width}
            height={height}
            extraHeight={labelExtraHeight}
            labelColor={labelColor}
        />}


    </>)
}

type LabelUpProps = {
    arc: number,
    width: number;
    extraHeight: number;
    labelColor: string;
    x: number;
    y: number;
}
const LabelUp = ({arc, width, extraHeight, labelColor, x, y}: LabelUpProps) => {

    return (<path d={`M ${x} ${y}
        m ${arc} -${arc + extraHeight}
        h ${width - arc - arc}
        a ${arc} ${arc} 0 0 1 ${arc} ${arc}
        v ${extraHeight}
        h -${width}
        v -${extraHeight}
        a ${arc} ${arc} 0 0 1 ${arc} -${arc}
        z`}
                  stroke="transparent"
                  fill={labelColor}
    />)
}
type LabelDownProps = {
    arc: number,
    width: number;
    height: number;
    extraHeight: number;
    labelColor: string;
    x: number;
    y: number;
}
const LabelDown = ({arc, width, height, extraHeight, labelColor, x, y}: LabelDownProps) => {

    return (<path d={`M ${x} ${y}
        m ${width} ${height}
        v ${extraHeight}
        a ${arc} ${arc} 0 0 1 -${arc} ${arc}
        h -${width - arc - arc}
        a ${arc} ${arc} 0 0 1 -${arc} -${arc}
        v -${extraHeight}
        z`}
                  stroke="transparent"
                  fill={labelColor}
    />)
}


function calcVerticalHolesString(height: number, numberOfHoles: number, upwards: boolean) {
    const totalVSize = height;
    const totalHoleSize = numberOfHoles * 10;
    const verticalRemaining = totalVSize - totalHoleSize;
    const verticalPart = Math.round(verticalRemaining / (numberOfHoles + 1));

    let verticalStr = "";
    if (upwards) {
        verticalStr += `v -${verticalPart} \n`;
    }
    for (let i = 0; i < numberOfHoles; i++) {
        if (upwards) {
            verticalStr += `c 20 20 20 -30 0 -10 \n`;
            verticalStr += `v -${verticalPart} \n`;
        } else {
            verticalStr += `v ${verticalPart} \n`;
            verticalStr += `c -20 -20 -20 30 0 10 \n`;
        }
    }
    if (!upwards) {
        verticalStr += `v ${verticalPart} \n`;
    }

    return verticalStr;
}

function calcHorizontalString(width: number, numberOfHoles: number, backwards: boolean) {
    const totalHSize = width;
    const totalHoleSize = numberOfHoles * 10;
    const remaining = totalHSize - totalHoleSize;
    const horizontalPart = Math.round(remaining / (numberOfHoles + 1));

    let result = "";
    if (backwards) {
        result += `h -${horizontalPart} \n`;
    } else {
        result += `h ${horizontalPart} \n`;
    }
    for (let i = 0; i < numberOfHoles; i++) {
        if (backwards) {
            result += `c 20 -20 -30 -20 -10 0 \n`;
            result += `h -${horizontalPart} \n`;
        } else {
            result += `c -20 20 30 20 10 0 \n`;
            result += `h ${horizontalPart} \n`;
        }
    }

    return result;
}
